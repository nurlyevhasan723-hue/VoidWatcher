package com.voidwatcher.system;

import com.voidwatcher.entity.WatcherEntity;
import com.voidwatcher.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class VoidWatcherSpawner {

    private VoidWatcherSpawner() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(VoidWatcherSpawner::tick);
    }

    private static void tick(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        if (time < 18000L || time >= 22000L) {
            return;
        }

        if (world.getRandom().nextInt(1600) != 0) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            if (!world.getEntitiesByType(
                    ModEntities.WATCHER,
                    player.getBoundingBox().expand(64.0),
                    entity -> entity.isAlive()
            ).isEmpty()) {
                continue;
            }

            BlockPos spawnPos = findForestSpawn(world, player);
            if (spawnPos == null) {
                continue;
            }

            WatcherEntity watcher = ModEntities.WATCHER.create(world);
            if (watcher == null) {
                return;
            }

            watcher.refreshPositionAndAngles(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5,
                    world.getRandom().nextFloat() * 360.0F,
                    0.0F
            );
            watcher.setTarget(player);
            world.spawnEntity(watcher);

            world.playSound(
                    null,
                    spawnPos,
                    net.minecraft.sound.SoundEvents.ENTITY_PHANTOM_AMBIENT,
                    net.minecraft.sound.SoundCategory.HOSTILE,
                    0.8F,
                    0.55F
            );
            return;
        }
    }

    private static BlockPos findForestSpawn(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d behind = new Vec3d(-look.x, 0.0, -look.z);
        if (behind.lengthSquared() < 0.01) {
            behind = new Vec3d(0.0, 0.0, 1.0);
        } else {
            behind = behind.normalize();
        }

        for (int i = 0; i < 12; i++) {
            double distance = 9.0 + world.getRandom().nextDouble() * 10.0;
            double jitter = (world.getRandom().nextDouble() - 0.5) * 8.0;
            int x = (int) Math.floor(player.getX() + behind.x * distance + jitter);
            int z = (int) Math.floor(player.getZ() + behind.z * distance + jitter);

            BlockPos column = new BlockPos(x, player.getBlockY(), z);
            if (!world.getWorldBorder().contains(column)) {
                continue;
            }

            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
                continue;
            }

            BlockPos floor = pos.down();
            if (!world.getBlockState(floor).isSolidBlock(world, floor)) {
                continue;
            }

            if (!hasTreeCover(world, pos)) {
                continue;
            }

            return pos;
        }

        return null;
    }

    private static boolean hasTreeCover(ServerWorld world, BlockPos center) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 5; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (world.getBlockState(mutable).isIn(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
