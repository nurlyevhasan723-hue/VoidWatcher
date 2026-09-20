package com.voidwatcher.entity;

import com.voidwatcher.registry.ModSounds;
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
import net.minecraft.world.Heightmap;
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
    private static final TrackedData<Boolean> SPEAKING =
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
    private int fakeStepCooldown;
    private int attackTicks;
    private int postAttackVanishTicks;
    private int dayObserveCooldown;
    private int voiceTicks;
    private int encounterLevel;
    private int adaptiveStage;

    public WatcherEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 25;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 240.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
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

        adaptiveStage = Math.min(10, (int) (world.getTime() / 24000L));

        if (postAttackVanishTicks > 0) {
            this.dataTracker.set(STARING, false);
            this.dataTracker.set(ATTACKING, false);
            this.dataTracker.set(SPEAKING, false);
            this.setInvisible(true);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);

            if (postAttackVanishTicks % 8 == 0) {
                world.spawnParticles(
                        ParticleTypes.LARGE_SMOKE,
                        this.getX(),
                        this.getY() + 1.0,
                        this.getZ(),
                        7,
                        0.35,
                        0.7,
                        0.35,
                        0.025
                );
            }

            return;
        }

        this.setInvisible(false);

        PlayerEntity player = world.getClosestPlayer(this, 72.0);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            this.dataTracker.set(STARING, false);
            this.dataTracker.set(ATTACKING, false);
            this.setTarget(null);
            return;
        }

        if (!isNight(world)) {
            dayObserve(world, player);
            return;
        }

        if (attackTicks > 0) {
            attackTicks--;
            this.dataTracker.set(ATTACKING, true);
        } else {
            this.dataTracker.set(ATTACKING, false);
        }

        if (voiceTicks > 0) {
            voiceTicks--;
            this.dataTracker.set(SPEAKING, true);
        } else {
            this.dataTracker.set(SPEAKING, false);
        }

        double distance = this.distanceTo(player);
        boolean beingWatched = isBeingWatched(player);
        this.dataTracker.set(STARING, beingWatched);

        if (beingWatched) {
            stareTicks++;
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
            this.getLookControl().lookAt(player, 40.0F, 40.0F);

            if (stareTicks == 1 || stareTicks % Math.max(22, 50 - adaptiveStage * 2) == 0) {
                world.playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE,
                        0.65F + adaptiveStage * 0.02F,
                        0.55F + world.getRandom().nextFloat() * 0.18F
                );
            }

            if (distance < 6.0 && vanishCooldown == 0) {
                vanishBehindPlayer(world, player);
                encounterLevel++;
                stareTicks = 0;
                vanishCooldown = Math.max(38, 80 - adaptiveStage * 3);
                triggerTerror(world, player, true);
                return;
            }
        } else {
            stareTicks = Math.max(0, stareTicks - 3);
            this.setTarget(player);

            double speed = 1.15 + adaptiveStage * 0.05 + Math.min(encounterLevel, 8) * 0.065;
            if (distance > 2.8) {
                this.getNavigation().startMovingTo(player, speed);
            } else {
                this.getNavigation().stop();
                openOrBreakNearbyDoor(world);
            }
        }

        if (distance < 34.0) {
            if (ambienceCooldown == 0) {
                ambienceCooldown = Math.max(28, 55 + world.getRandom().nextInt(90) - adaptiveStage * 4);
                playHorrorSound(world);
            }

            if (fakeStepCooldown == 0 && distance > 9.0 && distance < 28.0) {
                fakeStepCooldown = Math.max(35, 95 - adaptiveStage * 5);
                playFakeFootstep(world, player);
            }

            if (effectCooldown == 0 && distance < 16.0) {
                effectCooldown = Math.max(26, 55 + world.getRandom().nextInt(45) - adaptiveStage * 3);
                tormentPlayer(player);
            }

            if (terrorCooldown == 0 && distance < 13.0) {
                terrorCooldown = Math.max(60, 120 + world.getRandom().nextInt(120) - adaptiveStage * 6);
                triggerTerror(world, player, false);
            }

            if (environmentCooldown == 0 && distance < 36.0) {
                environmentCooldown = Math.max(100, 240 - adaptiveStage * 10);
                distortEnvironment(world);
            }

            if (chatCooldown == 0 && distance < 20.0) {
                chatCooldown = Math.max(70, 140 + world.getRandom().nextInt(120) - adaptiveStage * 5);
                sendHorrorMessage((ServerPlayerEntity) player);
            }

            if (blockBreakCooldown == 0) {
                blockBreakCooldown = Math.max(3, 5 - adaptiveStage / 3);
                breakAnythingButObsidian(world, player);
            }
        }

        if (this.age % 2 == 0) {
            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    1 + Math.min(5, encounterLevel / 2 + adaptiveStage / 2),
                    0.22,
                    0.45,
                    0.22,
                    0.004
            );
        }

        if (this.age % 7 == 0 && distance < 22.0) {
            world.spawnParticles(
                    ParticleTypes.PORTAL,
                    this.getX(),
                    this.getY() + 1.3,
                    this.getZ(),
                    4 + adaptiveStage / 2,
                    0.35,
                    0.7,
                    0.35,
                    0.08
            );
        }
    }

    private boolean isNight(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    private void dayObserve(ServerWorld world, PlayerEntity player) {
        this.setTarget(null);
        this.dataTracker.set(ATTACKING, false);
        this.dataTracker.set(SPEAKING, false);
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);
        this.getLookControl().lookAt(player, 20.0F, 20.0F);

        boolean lookingAtMe = isBeingWatched(player);
        this.dataTracker.set(STARING, lookingAtMe);

        double distance = this.distanceTo(player);
        if (dayObserveCooldown == 0 && (distance < 18.0 || distance > 52.0 || world.getRandom().nextInt(120 - adaptiveStage * 4) == 0)) {
            dayObserveCooldown = Math.max(45, 90 - adaptiveStage * 4);
            moveToObservationPoint(world, player);
        }

        if (this.age % 16 == 0 && distance < 64.0) {
            world.spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX(),
                    this.getY() + 0.9,
                    this.getZ(),
                    1 + adaptiveStage / 4,
                    0.1,
                    0.2,
                    0.1,
                    0.002
            );
        }

        // Rare daylight disappearance: it slips into the haze rather than attacking.
        if (distance < 14.0 && world.getRandom().nextInt(Math.max(60, 150 - adaptiveStage * 8)) == 0) {
            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    24,
                    0.5,
                    0.8,
                    0.5,
                    0.03
            );
            this.requestTeleport(
                    player.getX() + (world.getRandom().nextDouble() - 0.5) * 70.0,
                    player.getY(),
                    player.getZ() + (world.getRandom().nextDouble() - 0.5) * 70.0
            );
        }
    }

    private void moveToObservationPoint(ServerWorld world, PlayerEntity player) {
        for (int i = 0; i < 14; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = 28.0 + world.getRandom().nextDouble() * 18.0;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
                continue;
            }

            BlockPos floor = pos.down();
            if (!world.getBlockState(floor).isSolidBlock(world, floor)) {
                continue;
            }

            this.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            this.getLookControl().lookAt(player, 30.0F, 30.0F);
            return;
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
        if (fakeStepCooldown > 0) fakeStepCooldown--;
    }

    private boolean isBeingWatched(PlayerEntity player) {
        Vec3d toWatcher = this.getEyePos().subtract(player.getEyePos()).normalize();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double dot = look.dotProduct(toWatcher);
        return dot > Math.max(0.84, 0.90 - adaptiveStage * 0.006) && player.canSee(this);
    }

    private void tormentPlayer(PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 55 + adaptiveStage * 2, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 85 + adaptiveStage * 2, 0));

        if (this.getWorld().getRandom().nextInt(Math.max(2, 4 - adaptiveStage / 3)) == 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 45 + adaptiveStage * 2, 0));
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

        speak(world, player, teleport ? 0 : 5,
                teleport ? "НЕ ОБОРАЧИВАЙСЯ." : "ОН УЖЕ РЯДОМ.");
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

    private void playFakeFootstep(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d behind = new Vec3d(-look.x, 0.0, -look.z);
        if (behind.lengthSquared() < 0.01) {
            behind = new Vec3d(0.0, 0.0, 1.0);
        } else {
            behind = behind.normalize();
        }

        double distance = 4.0 + world.getRandom().nextDouble() * 4.0;
        BlockPos soundPos = BlockPos.ofFloored(player.getPos().add(behind.multiply(distance)));
        world.playSound(
                null,
                soundPos,
                world.getRandom().nextBoolean() ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_CHAIN_STEP,
                SoundCategory.HOSTILE,
                0.75F + adaptiveStage * 0.02F,
                0.55F + world.getRandom().nextFloat() * 0.25F
        );
    }

    private void distortEnvironment(ServerWorld world) {
        long current = world.getTimeOfDay() % 24000L;
        long night = 13500L + world.getRandom().nextInt(7000);
        if (current < 13000L || current > 23000L) {
            world.setTimeOfDay(night);
        } else if (world.getRandom().nextInt(Math.max(2, 4 - adaptiveStage / 3)) == 0) {
            world.setTimeOfDay(night);
        }

        boolean thunder = world.getRandom().nextBoolean();
        world.setWeather(0, Math.max(500, 900 - adaptiveStage * 20), true, thunder);
    }

    private void sendHorrorMessage(ServerPlayerEntity player) {
        int line = this.getWorld().getRandom().nextInt(8);
        switch (line) {
            case 0 -> speak((ServerWorld) this.getWorld(), player, 0, "НЕ ОБОРАЧИВАЙСЯ");
            case 1 -> speak((ServerWorld) this.getWorld(), player, 1, "ОН СМОТРИТ НА ТЕБЯ");
            case 2 -> speak((ServerWorld) this.getWorld(), player, 2, "БЕГИ");
            case 3 -> speak((ServerWorld) this.getWorld(), player, 3, "ТЫ УЖЕ НЕ ОДИН");
            case 4 -> speak((ServerWorld) this.getWorld(), player, 4, "НЕ ДАЙ ЕМУ УВИДЕТЬ ТЕБЯ");
            case 5 -> speak((ServerWorld) this.getWorld(), player, 5, "ОН УЖЕ РЯДОМ");
            case 6 -> speak((ServerWorld) this.getWorld(), player, 6, "КТО-ТО ДЫШИТ ТЕБЕ В СПИНУ");
            default -> speak((ServerWorld) this.getWorld(), player, 7, "ОН СЛЫШИТ ТЕБЯ");
        }
    }

    private void speak(ServerWorld world, PlayerEntity player, int line, String message) {
        player.sendMessage(
                Text.literal("[VOID] " + message).formatted(Formatting.DARK_RED, Formatting.BOLD),
                false
        );

        voiceTicks = 42;
        dataTracker.set(SPEAKING, true);

        world.playSound(
                null,
                player.getBlockPos(),
                ModSounds.getVoice(line),
                SoundCategory.HOSTILE,
                1.0F,
                0.92F + world.getRandom().nextFloat() * 0.12F
        );
    }

    private void breakAnythingButObsidian(ServerWorld world, PlayerEntity player) {
        if (!isNight(world)) {
            return;
        }

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
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
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

    private void openOrBreakNearbyDoor(ServerWorld world) {
        BlockPos origin = this.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(pos);

                    if (!state.isIn(BlockTags.DOORS)) {
                        continue;
                    }

                    if (state.contains(Properties.OPEN) && !state.get(Properties.OPEN)) {
                        world.setBlockState(pos, state.with(Properties.OPEN, true), Block.NOTIFY_ALL);
                        world.playSound(
                                null,
                                pos,
                                SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                                SoundCategory.HOSTILE,
                                1.0F,
                                0.65F
                        );
                        return;
                    }

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

    @Override
    public boolean tryAttack(Entity target) {
        if (!(this.getWorld() instanceof ServerWorld world) || !isNight(world) || postAttackVanishTicks > 0) {
            return false;
        }

        boolean hit = super.tryAttack(target);
        if (hit) {
            attackTicks = 12;
            this.dataTracker.set(ATTACKING, true);

            if (target instanceof PlayerEntity player) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 35, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 55, 0));
                speak(world, (ServerPlayerEntity) player, 2, "БЕГИ");

                postAttackVanishTicks = Math.max(180, 240 - adaptiveStage * 6);
                this.setInvisible(true);
                this.setTarget(null);
                this.getNavigation().stop();
                vanishAfterHit(world, player);
            }
        }
        return hit;
    }

    private void vanishAfterHit(ServerWorld world, PlayerEntity player) {
        Vec3d away = this.getPos().subtract(player.getPos());
        if (away.lengthSquared() < 0.01) {
            away = player.getRotationVec(1.0F).multiply(-1.0);
        } else {
            away = away.normalize();
        }

        for (int i = 0; i < 10; i++) {
            double distance = 18.0 + world.getRandom().nextDouble() * 14.0;
            int x = (int) Math.floor(player.getX() + away.x * distance);
            int z = (int) Math.floor(player.getZ() + away.z * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
                continue;
            }

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    30,
                    0.5,
                    0.8,
                    0.5,
                    0.04
            );

            this.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return;
        }
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

    public int getAdaptiveStage() {
        return adaptiveStage;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("VoidWatcherEncounters", encounterLevel);
        nbt.putInt("VoidWatcherVanish", postAttackVanishTicks);
        nbt.putInt("VoidWatcherAdaptiveStage", adaptiveStage);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        encounterLevel = nbt.getInt("VoidWatcherEncounters");
        postAttackVanishTicks = nbt.getInt("VoidWatcherVanish");
        adaptiveStage = nbt.getInt("VoidWatcherAdaptiveStage");
        this.setInvisible(postAttackVanishTicks > 0);
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
