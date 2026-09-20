package com.voidwatcher.system;

import com.voidwatcher.entity.WatcherEntity;
import com.voidwatcher.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

        // Active only during the full night.
        if (time < 13000L || time >= 23000L) {
            return;
        }

        // Rare enough for Hardcore, but frequent enough to make nights unsafe.
        if (world.getRandom().nextInt(420) != 0) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            // One active Watcher per player keeps the encounter scary without turning
            // the world into a permanent mob swarm.
            int watcherCount = world.getEntitiesByType(
                    ModEntities.WATCHER,
                    player.getBoundingBox().expand(96.0),
                    entity -> entity.isAlive()
            ).size();

            if (watcherCount > 0) {
                continue;
            }

            BlockPos spawnPos = findNightSpawn(world, player);
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
                    net.minecraft.sound.SoundEvents.ENTITY_PHANTOM_FLAP,
                    net.minecraft.sound.SoundCategory.HOSTILE,
                    0.65F,
                    0.42F
            );
            return;
        }
    }

    private static BlockPos findNightSpawn(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d behind = new Vec3d(-look.x, 0.0, -look.z);

        if (behind.lengthSquared() < 0.01) {
            behind = new Vec3d(0.0, 0.0, 1.0);
        } else {
            behind = behind.normalize();
        }

        for (int i = 0; i < 24; i++) {
            double distance = 16.0 + world.getRandom().nextDouble() * 12.0;
            double side = (world.getRandom().nextDouble() - 0.5) * 10.0;

            int x = (int) Math.floor(player.getX() + behind.x * distance + look.z * side);
            int z = (int) Math.floor(player.getZ() + behind.z * distance - look.x * side);

            if (!world.getWorldBorder().contains(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }

            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.getBlockState(pos).isAir()
                    || !world.getBlockState(pos.up()).isAir()
                    || !world.getBlockState(pos.up(2)).isAir()) {
                continue;
            }

            BlockPos floor = pos.down();
            if (!world.getBlockState(floor).isSolidBlock(world, floor)) {
                continue;
            }

            return pos;
        }

        return null;
    }
}
