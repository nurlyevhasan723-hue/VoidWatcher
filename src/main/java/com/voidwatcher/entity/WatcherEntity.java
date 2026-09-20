package com.voidwatcher.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public class WatcherEntity extends HostileEntity implements GeoEntity {

    private static final TrackedData<Boolean> STARING =
            DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ATTACKING =
            DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int stareTicks;
    private int vanishCooldown;
    private int ambienceCooldown;
    private int terrorCooldown;
    private int environmentCooldown;
    private int effectCooldown;
    private int blockBreakCooldown;
    private int chatCooldown;
    private int attackTicks;
    private int encounterLevel;

    public WatcherEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 25;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 240.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STARING, false);
        builder.add(ATTACKING, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.30, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.90));
        this.goalSelector.add(3, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        tickTimers();

        if (attackTicks > 0) {
            attackTicks--;
            this.dataTracker.set(ATTACKING, true);
        } else {
            this.dataTracker.set(ATTACKING, false);
        }

        PlayerEntity player = world.getClosestPlayer(this, 72.0);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            this.dataTracker.set(STARING, false);
            return;
        }

        double distance = this.distanceTo(player);
        boolean beingWatched = isBeingWatched(player);
        this.dataTracker.set(STARING, beingWatched);

        if (beingWatched) {
            stareTicks++;
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
            this.getLookControl().lookAt(player, 40.0F, 40.0F);

            if (stareTicks == 1 || stareTicks % 50 == 0) {
                world.playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE,
                        0.65F,
                        0.55F + world.getRandom().nextFloat() * 0.18F
                );
            }

            if (distance < 6.0 && vanishCooldown == 0) {
                vanishBehindPlayer(world, player);
                encounterLevel++;
                stareTicks = 0;
                vanishCooldown = 80;
                triggerTerror(world, player, true);
                return;
            }
        } else {
            stareTicks = Math.max(0, stareTicks - 3);
            this.setTarget(player);

            double speed = 1.15 + Math.min(encounterLevel, 6) * 0.06;
            if (distance > 2.8) {
                this.getNavigation().startMovingTo(player, speed);
            } else {
                this.getNavigation().stop();
                tryBreakNearbyDoor(world);
            }
        }

        if (distance < 30.0) {
            if (ambienceCooldown == 0) {
                ambienceCooldown = 55 + world.getRandom().nextInt(90);
                playHorrorSound(world);
            }

            if (effectCooldown == 0 && distance < 16.0) {
                effectCooldown = 55 + world.getRandom().nextInt(45);
                tormentPlayer(player);
            }

            if (terrorCooldown == 0 && distance < 13.0) {
                terrorCooldown = 120 + world.getRandom().nextInt(120);
                triggerTerror(world, player, false);
            }

            if (environmentCooldown == 0 && distance < 36.0) {
                environmentCooldown = 240;
                distortEnvironment(world);
            }

            if (chatCooldown == 0 && distance < 20.0) {
                chatCooldown = 140 + world.getRandom().nextInt(120);
                sendHorrorMessage((ServerPlayerEntity) player);
            }

            if (blockBreakCooldown == 0) {
                blockBreakCooldown = 5;
                breakAnythingButObsidian(world, player);
            }
        }

        if (this.age % 2 == 0) {
            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    1 + Math.min(encounterLevel, 4),
                    0.22,
                    0.45,
                    0.22,
                    0.004
            );
        }

        if (this.age % 7 == 0 && distance < 18.0) {
            world.spawnParticles(
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + 1.3,
                    this.getZ(),
                    4,
                    0.35,
                    0.7,
                    0.35,
                    0.08
            );
        }
    }

    private void tickTimers() {
        if (vanishCooldown > 0) vanishCooldown--;
        if (ambienceCooldown > 0) ambienceCooldown--;
        if (terrorCooldown > 0) terrorCooldown--;
        if (environmentCooldown > 0) environmentCooldown--;
        if (effectCooldown > 0) effectCooldown--;
        if (blockBreakCooldown > 0) blockBreakCooldown--;
        if (chatCooldown > 0) chatCooldown--;
    }

    private boolean isBeingWatched(PlayerEntity player) {
        Vec3d toWatcher = this.getEyePos().subtract(player.getEyePos()).normalize();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double dot = look.dotProduct(toWatcher);
        return dot > 0.90 && player.canSee(this);
    }

    private void tormentPlayer(PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 55, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 85, 0));

        if (this.getWorld().getRandom().nextInt(3) == 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 45, 0));
        }
    }

    private void triggerTerror(ServerWorld world, PlayerEntity player, boolean teleport) {
        world.playSound(
                null,
                player.getBlockPos(),
                teleport ? SoundEvents.ENTITY_WARDEN_ROAR : SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY,
                SoundCategory.HOSTILE,
                teleport ? 1.15F : 0.80F,
                0.45F + world.getRandom().nextFloat() * 0.20F
        );

        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                    Text.literal(world.getRandom().nextBoolean()
                            ? "НЕ ОБОРАЧИВАЙСЯ."
                            : "ОН УЖЕ РЯДОМ.")
                            .formatted(Formatting.DARK_RED, Formatting.BOLD),
                    true
            );
        }
    }

    private void playHorrorSound(ServerWorld world) {
        if (world.getRandom().nextBoolean()) {
            world.playSound(
                    null,
                    this.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_AMBIENT,
                    SoundCategory.HOSTILE,
                    0.60F,
                    0.45F + world.getRandom().nextFloat() * 0.25F
            );
        } else {
            world.playSound(
                    null,
                    this.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_STEP,
                    SoundCategory.HOSTILE,
                    0.75F,
                    0.55F + world.getRandom().nextFloat() * 0.25F
            );
        }
    }

    private void distortEnvironment(ServerWorld world) {
        long current = world.getTimeOfDay() % 24000L;
        long night = 13500L + world.getRandom().nextInt(7000);
        if (current < 13000L || current > 23000L) {
            world.setTimeOfDay(night);
        } else if (world.getRandom().nextInt(3) == 0) {
            world.setTimeOfDay(night);
        }

        boolean thunder = world.getRandom().nextBoolean();
        world.setWeather(0, 900, true, thunder);
    }

    private void sendHorrorMessage(ServerPlayerEntity player) {
        String[] messages = {
                "кто-то дышит тебе в спину...",
                "НЕ СМОТРИ ЕМУ В ГЛАЗА",
                "ты здесь не один.",
                "он слышит тебя.",
                "беги."
        };
        String message = messages[this.getWorld().getRandom().nextInt(messages.length)];
        player.sendMessage(
                Text.literal("[VOID] " + message).formatted(
                        Formatting.DARK_GRAY,
                        Formatting.ITALIC
                ),
                false
        );
    }

    private void breakAnythingButObsidian(ServerWorld world, PlayerEntity player) {
        Vec3d toPlayer = player.getPos().subtract(this.getPos());
        if (toPlayer.lengthSquared() < 0.01) {
            return;
        }

        Vec3d dir = toPlayer.normalize();

        for (int i = 1; i <= 3; i++) {
            int x = (int) Math.floor(this.getX() + dir.x * i);
            int y = (int) Math.floor(this.getY() + 1.0);
            int z = (int) Math.floor(this.getZ() + dir.z * i);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);

            if (isIndestructible(state) || state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }

            world.breakBlock(pos, true, this);
            world.playSound(
                    null,
                    pos,
                    state.getSoundGroup().getBreakSound(),
                    SoundCategory.HOSTILE,
                    0.85F,
                    0.65F
            );
            return;
        }

        BlockPos around = this.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    mutable.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (isIndestructible(state) || state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    world.breakBlock(mutable, true, this);
                    return;
                }
            }
        }
    }

    private boolean isIndestructible(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.BEDROCK
                || block == Blocks.BARRIER
                || block == Blocks.END_PORTAL
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.COMMAND_BLOCK
                || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK
                || block == Blocks.STRUCTURE_BLOCK
                || block == Blocks.JIGSAW;
    }

    private void vanishBehindPlayer(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        double baseX = player.getX() - look.x * (10.0 + world.getRandom().nextDouble() * 10.0);
        double baseZ = player.getZ() - look.z * (10.0 + world.getRandom().nextDouble() * 10.0);

        for (int i = 0; i < 16; i++) {
            double angle = (world.getRandom().nextDouble() - 0.5) * 1.6;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double dx = look.x * cos - look.z * sin;
            double dz = look.x * sin + look.z * cos;

            int x = (int) Math.floor(player.getX() - dx * (9.0 + world.getRandom().nextDouble() * 11.0));
            int z = (int) Math.floor(player.getZ() - dz * (9.0 + world.getRandom().nextDouble() * 11.0));
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
                continue;
            }

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.2,
                    this.getZ(),
                    24,
                    0.5,
                    0.8,
                    0.5,
                    0.03
            );

            this.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            this.setTarget(player);

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.2,
                    this.getZ(),
                    28,
                    0.5,
                    0.8,
                    0.5,
                    0.03
            );
            return;
        }

        this.requestTeleport(baseX, player.getY(), baseZ);
    }

    private void tryBreakNearbyDoor(ServerWorld world) {
        BlockPos origin = this.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (world.getBlockState(pos).isIn(BlockTags.DOORS)) {
                        world.breakBlock(pos, true, this);
                        world.playSound(
                                null,
                                pos,
                                SoundEvents.BLOCK_WOOD_BREAK,
                                SoundCategory.HOSTILE,
                                0.95F,
                                0.55F
                        );
                        return;
                    }
                }
            }
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            attackTicks = 12;
            this.dataTracker.set(ATTACKING, true);

            if (target instanceof PlayerEntity player) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 35, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 55, 0));
            }
        }
        return hit;
    }

    public boolean isStaring() {
        return this.dataTracker.get(STARING);
    }

    public boolean isAttackingAnimation() {
        return this.dataTracker.get(ATTACKING);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("VoidWatcherEncounters", encounterLevel);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        encounterLevel = nbt.getInt("VoidWatcherEncounters");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "controller",
                2,
                state -> {
                    if (this.dataTracker.get(ATTACKING)) {
                        return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
                    }
                    if (this.dataTracker.get(STARING)) {
                        return state.setAndContinue(RawAnimation.begin().thenLoop("stare"));
                    }
                    if (state.isMoving()) {
                        return state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
                    }
                    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
                }
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return super.getTarget();
    }
}
