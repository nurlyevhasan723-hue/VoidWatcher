package com.voidwatcher.registry;

import com.voidwatcher.VoidWatcherMod;
import com.voidwatcher.entity.WatcherEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<WatcherEntity> WATCHER =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(VoidWatcherMod.MOD_ID, "watcher"),
                    EntityType.Builder.create(WatcherEntity::new, SpawnGroup.MONSTER
)
                            .dimensions(0.8f, 2.4f)
                            .build()
            );

    public static void register() {
        // Нужно только чтобы вызвать регистрацию класса.
    }
}