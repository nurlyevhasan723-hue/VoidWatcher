package com.voidwatcher;

import com.voidwatcher.entity.WatcherEntity;
import com.voidwatcher.registry.ModEntities;
import com.voidwatcher.system.VoidWatcherSpawner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class VoidWatcherMod implements ModInitializer {

    public static final String MOD_ID = "voidwatcher";

    @Override
    public void onInitialize() {
        ModEntities.register();

        FabricDefaultAttributeRegistry.register(
                ModEntities.WATCHER,
                WatcherEntity.createAttributes()
        );

        VoidWatcherSpawner.register();
    }
}
