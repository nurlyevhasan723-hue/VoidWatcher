package com.voidwatcher.registry;

import com.voidwatcher.VoidWatcherMod;
import com.voidwatcher.entity.WatcherEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {

    public static final EntityType<WatcherEntity> WATCHER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(VoidWatcherMod.MOD_ID, "watcher"),
            EntityType.Builder.create(WatcherEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 2.8f)
                    .maxTrackingRange(64)
                    .trackingTickInterval(2)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        // Static registration is completed when this class is loaded.
    }
}
