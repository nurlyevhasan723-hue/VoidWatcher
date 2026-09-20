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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
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
    private static final TrackedData<Boolean> SPEAKING =
            DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int stareTicks;
    private int scareCooldown;
    private int heartbeatCooldown;
    private int effectCooldown;
    private int blockBreakCooldown;
    private int fakeStepCooldown;
    private int attackTicks;
    private int vanishTicks;
    private int dayTicks;
    private int lastWarnTick = -1000;
    private int speakingTicks;

    public WatcherEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 18;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 120.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STARING, false);
        builder.add(ATTACKING, false);
        builder.add(SPEAKING, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.18, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.78));
        this.goalSelector.add(3, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        tickTimers();
        if (speakingTicks > 0) {
            speakingTicks--;
            this.dataTracker.set(SPEAKING, true);
        } else {
            this.dataTracker.set(SPEAKING, false);
        }

        if (vanishTicks > 0) {
            vanishTicks--;
            this.setInvisible(true);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);

            if (vanishTicks % 10 == 0) {
                world.spawnParticles(
                        ParticleTypes.LARGE_SMOKE,
                        this.getX(),
                        this.getY() + 1.0,
                        this.getZ(),
                        8,
                        0.35,
                        0.55,
                        0.35,
                        0.02
                );
            }

            if (vanishTicks == 0) {
                this.discard();
            }
            return;
        }

        this.setInvisible(false);

        PlayerEntity player = world.getClosestPlayer(this, 64.0);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            this.setTarget(null);
            return;
        }

        if (!isNight(world)) {
            dayBehavior(world, player);
            return;
        }

        this.dayTicks = 0;

        double distance = this.distanceTo(player);
        boolean watched = isBeingWatched(player);

        this.dataTracker.set(STARING, watched);

        if (watched) {
            handleStare(world, player, distance);
        } else {
            handleHunt(world, player, distance);
        }

        if (distance < 22.0) {
            if (heartbeatCooldown == 0) {
                heartbeatCooldown = 55 + world.getRandom().nextInt(45);
                world.playSound(
                        null,
                        player.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE,
                        0.55F,
                        0.58F + world.getRandom().nextFloat() * 0.14F
                );
            }

            if (fakeStepCooldown == 0 && distance > 7.0) {
                fakeStepCooldown = 95;
                fakeFootstep(world, player);
            }

            if (effectCooldown == 0 && distance < 13.0) {
                effectCooldown = 80;
                applyFear(player);
            }

            if (scareCooldown == 0 && distance < 10.0) {
                scareCooldown = 150;
                sendWhisper(world, player);
            }

            if (blockBreakCooldown == 0 && distance < 5.0) {
                blockBreakCooldown = 60;
                breakOneObstacle(world, player);
            }
        }

        if (this.age % 3 == 0 && distance < 30.0) {
            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    1,
                    0.18,
                    0.35,
                    0.18,
                    0.004
            );
        }
    }

    private boolean isNight(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    private void handleStare(ServerWorld world, PlayerEntity player, double distance) {
        stareTicks++;
        this.setTarget(null);
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);
        this.getLookControl().lookAt(player, 35.0F, 35.0F);

        if (stareTicks == 1) {
            world.playSound(
                    null,
                    this.getBlockPos(),
                    SoundEvents.ENTITY_ENDERMAN_STARE,
                    SoundCategory.HOSTILE,
                    0.65F,
                    0.48F
            );
        }

        // It remains frozen while watched. After enough eye contact it relocates,
        // keeping the encounter frightening without forcing an unfair hit.
        if (stareTicks > 55 && distance > 7.0 && scareCooldown == 0) {
            scareCooldown = 110;
            teleportBehind(world, player);
            stareTicks = 0;
            sendWhisper(world, player);
        }
    }

    private void handleHunt(ServerWorld world, PlayerEntity player, double distance) {
        stareTicks = Math.max(0, stareTicks - 3);
        this.setTarget(player);

        double speed = distance < 9.0 ? 1.02 : 1.14;
        this.getNavigation().startMovingTo(player, speed);

        // Every so often the Watcher stops chasing for a second, then resumes.
        if (this.age % 50 == 0 && distance > 12.0 && world.getRandom().nextInt(4) == 0) {
            this.getNavigation().stop();
        }

        if (attackTicks > 0) {
            attackTicks--;
            this.dataTracker.set(ATTACKING, true);
        } else {
            this.dataTracker.set(ATTACKING, false);
        }
    }

    private void dayBehavior(ServerWorld world, PlayerEntity player) {
        dayTicks++;
        this.setTarget(null);
        this.dataTracker.set(STARING, false);
        this.dataTracker.set(ATTACKING, false);
        this.dataTracker.set(SPEAKING, false);
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);

        if (dayTicks == 1) {
            this.dataTracker.set(STARING, true);
            world.playSound(
                    null,
                    this.getBlockPos(),
                    SoundEvents.ENTITY_PHANTOM_AMBIENT,
                    SoundCategory.HOSTILE,
                    0.45F,
                    0.55F
            );
        }

        // In daylight it watches instead of attacking, then fades out.
        if (dayTicks > 80) {
            this.setInvisible(true);
            this.vanishTicks = 8;
        } else {
            this.getLookControl().lookAt(player, 20.0F, 20.0F);
        }
    }

    private void tickTimers() {
        if (scareCooldown > 0) scareCooldown--;
        if (heartbeatCooldown > 0) heartbeatCooldown--;
        if (effectCooldown > 0) effectCooldown--;
        if (blockBreakCooldown > 0) blockBreakCooldown--;
        if (fakeStepCooldown > 0) fakeStepCooldown--;
    }

    private boolean isBeingWatched(PlayerEntity player) {
        Vec3d toWatcher = this.getEyePos().subtract(player.getEyePos());
        if (toWatcher.lengthSquared() < 0.01) {
            return true;
        }

        toWatcher = toWatcher.normalize();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double dot = look.dotProduct(toWatcher);

        return dot > 0.92 && player.canSee(this);
    }

    private void applyFear(PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 24, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 24, 0));
    }

    private void fakeFootstep(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d behind = new Vec3d(-look.x, 0.0, -look.z);

        if (behind.lengthSquared() < 0.01) {
            behind = new Vec3d(0.0, 0.0, 1.0);
        } else {
            behind = behind.normalize();
        }

        double distance = 4.0 + world.getRandom().nextDouble() * 3.0;
        BlockPos pos = BlockPos.ofFloored(player.getPos().add(behind.multiply(distance)));

        world.playSound(
                null,
                pos,
                world.getRandom().nextBoolean()
                        ? SoundEvents.BLOCK_STONE_STEP
                        : SoundEvents.BLOCK_CHAIN_STEP,
                SoundCategory.HOSTILE,
                0.65F,
                0.58F + world.getRandom().nextFloat() * 0.18F
        );
    }

    private void sendWhisper(ServerWorld world, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        String[] messages = {
                "НЕ ОБОРАЧИВАЙСЯ",
                "ОН СТОИТ ЗА ТОБОЙ",
                "ОН СЛУШАЕТ",
                "ТИШЕ",
                "НЕ СМОТРИ",
                "ТЫ НЕ ОДИН"
        };

        String message = messages[world.getRandom().nextInt(messages.length)];

        serverPlayer.sendMessage(
                Text.literal("[VOID] " + message)
                        .formatted(Formatting.DARK_RED, Formatting.BOLD),
                false
        );

        this.dataTracker.set(SPEAKING, true);
        speakingTicks = 28;

        world.playSound(
                null,
                serverPlayer.getBlockPos(),
                world.getRandom().nextBoolean()
                        ? SoundEvents.ENTITY_WARDEN_AMBIENT
                        : SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY,
                SoundCategory.HOSTILE,
                0.72F,
                0.55F + world.getRandom().nextFloat() * 0.15F
        );

        // Keep the talking animation visible without creating a real-time task.
        lastWarnTick = this.age;
    }

    private void breakOneObstacle(ServerWorld world, PlayerEntity player) {
        Vec3d dir = player.getPos().subtract(this.getPos());

        if (dir.lengthSquared() < 0.01) {
            return;
        }

        dir = dir.normalize();

        // First preference: a closed door in the path.
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 1; i <= 2; i++) {
            int x = (int) Math.floor(this.getX() + dir.x * i);
            int y = (int) Math.floor(this.getY());
            int z = (int) Math.floor(this.getZ() + dir.z * i);
            mutable.set(x, y, z);

            var state = world.getBlockState(mutable);
            if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS)) {
                world.breakBlock(mutable, true, this);
                world.playSound(
                        null,
                        mutable,
                        SoundEvents.BLOCK_WOOD_BREAK,
                        SoundCategory.HOSTILE,
                        0.85F,
                        0.60F
                );
                return;
            }
        }

        // Otherwise break exactly one ordinary obstruction. Never touch protected blocks.
        BlockPos pos = BlockPos.ofFloored(
                this.getX() + dir.x * 1.6,
                this.getY() + 1.0,
                this.getZ() + dir.z * 1.6
        );
        var state = world.getBlockState(pos);

        if (state.isAir() || !state.getFluidState().isEmpty() || isProtected(state.getBlock())) {
            return;
        }

        world.breakBlock(pos, true, this);
        world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_STONE_BREAK,
                SoundCategory.HOSTILE,
                0.60F,
                0.72F
        );
    }

    private boolean isProtected(net.minecraft.block.Block block) {
        return block == net.minecraft.block.Blocks.OBSIDIAN
                || block == net.minecraft.block.Blocks.CRYING_OBSIDIAN
                || block == net.minecraft.block.Blocks.BEDROCK
                || block == net.minecraft.block.Blocks.BARRIER
                || block == net.minecraft.block.Blocks.END_PORTAL
                || block == net.minecraft.block.Blocks.END_PORTAL_FRAME
                || block == net.minecraft.block.Blocks.COMMAND_BLOCK
                || block == net.minecraft.block.Blocks.CHAIN_COMMAND_BLOCK
                || block == net.minecraft.block.Blocks.REPEATING_COMMAND_BLOCK
                || block == net.minecraft.block.Blocks.STRUCTURE_BLOCK
                || block == net.minecraft.block.Blocks.JIGSAW;
    }

    private void teleportBehind(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d base = new Vec3d(-look.x, 0.0, -look.z);

        if (base.lengthSquared() < 0.01) {
            base = new Vec3d(0.0, 0.0, 1.0);
        } else {
            base = base.normalize();
        }

        for (int i = 0; i < 14; i++) {
            double distance = 8.0 + world.getRandom().nextDouble() * 8.0;
            double side = (world.getRandom().nextDouble() - 0.5) * 6.0;

            int x = (int) Math.floor(player.getX() + base.x * distance + side);
            int z = (int) Math.floor(player.getZ() + base.z * distance - side);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir()
                    || !world.getBlockState(pos.up()).isAir()
                    || !world.getBlockState(pos.up(2)).isAir()) {
                continue;
            }

            if (!world.getWorldBorder().contains(pos)) {
                continue;
            }

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    18,
                    0.4,
                    0.7,
                    0.4,
                    0.03
            );

            this.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    22,
                    0.4,
                    0.7,
                    0.4,
                    0.03
            );
            return;
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        if (!(this.getWorld() instanceof ServerWorld world) || !isNight(world) || vanishTicks > 0) {
            return false;
        }

        boolean hit = super.tryAttack(target);
        if (!hit) {
            return false;
        }

        this.attackTicks = 14;
        this.dataTracker.set(ATTACKING, true);

        if (target instanceof PlayerEntity player) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 28, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 0));

            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(
                        Text.literal("[VOID] БЕГИ")
                                .formatted(Formatting.DARK_RED, Formatting.BOLD),
                        false
                );
            }

            // One solid hit, then it disappears for several seconds.
            vanishTicks = 150 + world.getRandom().nextInt(50);
            this.setInvisible(true);
            this.setTarget(null);
            this.getNavigation().stop();
            teleportBehind(world, player);
        }

        return true;
    }

    public boolean isStaring() {
        return this.dataTracker.get(STARING);
    }

    public boolean isAttackingAnimation() {
        return this.dataTracker.get(ATTACKING);
    }

    public boolean isSpeaking() {
        return this.dataTracker.get(SPEAKING);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("VoidWatcherVanishTicks", vanishTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        vanishTicks = nbt.getInt("VoidWatcherVanishTicks");
        this.setInvisible(vanishTicks > 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "controller",
                2,
                state -> {
                    if (this.dataTracker.get(SPEAKING)) {
                        return state.setAndContinue(RawAnimation.begin().thenLoop("talk"));
                    }
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
