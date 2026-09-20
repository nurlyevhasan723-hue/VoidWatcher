package com.voidwatcher;

import com.voidwatcher.client.render.WatcherRenderer;
import com.voidwatcher.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class VoidWatcherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherRenderer::new);
    }
}