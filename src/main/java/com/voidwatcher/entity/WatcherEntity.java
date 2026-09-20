package com.voidwatcher.entity;

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
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int stareTicks;
    private int vanishCooldown;
    private int ambienceCooldown;
    private int encounterLevel;

    public WatcherEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 15;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 120.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STARING, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.05, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.72));
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

        if (vanishCooldown > 0) {
            vanishCooldown--;
        }
        if (ambienceCooldown > 0) {
            ambienceCooldown--;
        }

        PlayerEntity player = world.getClosestPlayer(this, 48.0);
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
            this.getLookControl().lookAt(player, 30.0F, 30.0F);

            if (stareTicks == 1 || stareTicks % 80 == 0) {
                world.playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.ENTITY_ENDERMAN_STARE,
                        SoundCategory.HOSTILE,
                        0.35F,
                        0.55F + world.getRandom().nextFloat() * 0.2F
                );
            }

            if (distance < 5.0 && vanishCooldown == 0) {
                vanishBehindPlayer(world, player);
                encounterLevel++;
                stareTicks = 0;
                vanishCooldown = 100;
                return;
            }
        } else {
            stareTicks = Math.max(0, stareTicks - 2);
            this.setTarget(player);

            double speed = 0.95 + Math.min(encounterLevel, 4) * 0.04;
            if (distance > 3.5) {
                this.getNavigation().startMovingTo(player, speed);
            } else {
                this.getNavigation().stop();
                tryBreakNearbyDoor(world);
            }
        }

        if (distance < 24.0 && ambienceCooldown == 0) {
            ambienceCooldown = 100 + world.getRandom().nextInt(140);

            if (world.getRandom().nextBoolean()) {
                world.playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.ENTITY_PHANTOM_AMBIENT,
                        SoundCategory.HOSTILE,
                        0.35F,
                        0.55F + world.getRandom().nextFloat() * 0.25F
                );
            } else {
                world.playSound(
                        null,
                        this.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_STEP,
                        SoundCategory.HOSTILE,
                        0.30F,
                        0.65F + world.getRandom().nextFloat() * 0.20F
                );
            }
        }

        if (this.age % 3 == 0) {
            world.spawnParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + 1.0,
                    this.getZ(),
                    2 + Math.min(encounterLevel, 3),
                    0.25,
                    0.35,
                    0.25,
                    0.002
            );
        }
    }

    private boolean isBeingWatched(PlayerEntity player) {
        Vec3d toWatcher = this.getEyePos().subtract(player.getEyePos()).normalize();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double dot = look.dotProduct(toWatcher);
        return dot > 0.92 && player.canSee(this);
    }

    private void vanishBehindPlayer(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        double baseX = player.getX() - look.x * (9.0 + world.getRandom().nextDouble() * 7.0);
        double baseZ = player.getZ() - look.z * (9.0 + world.getRandom().nextDouble() * 7.0);

        for (int i = 0; i < 10; i++) {
            double angle = (world.getRandom().nextDouble() - 0.5) * 1.4;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double dx = look.x * cos - look.z * sin;
            double dz = look.x * sin + look.z * cos;

            int x = (int) Math.floor(player.getX() - dx * (8.0 + world.getRandom().nextDouble() * 8.0));
            int z = (int) Math.floor(player.getZ() - dz * (8.0 + world.getRandom().nextDouble() * 8.0));
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
                    16,
                    0.4,
                    0.7,
                    0.4,
                    0.03
            );

            this.requestTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            this.setTarget(player);

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    this.getX(),
                    this.getY() + 1.2,
                    this.getZ(),
                    18,
                    0.4,
                    0.7,
                    0.4,
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
                                SoundEvents.BLOCK_WOODEN_DOOR_BREAK,
                                SoundCategory.HOSTILE,
                                0.9F,
                                0.65F
                        );
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("VoidWatcherEncounters", encounterLevel);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        encounterLevel = nbt.getInt("VoidWatcherEncounters");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "controller",
                4,
                state -> {
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
