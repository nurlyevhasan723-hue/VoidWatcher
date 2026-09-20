package com.voidwatcher.client.render;

import com.voidwatcher.VoidWatcherMod;
import com.voidwatcher.entity.WatcherEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class WatcherModel extends GeoModel<WatcherEntity> {

    @Override
    public Identifier getModelResource(WatcherEntity entity) {
        return Identifier.of(VoidWatcherMod.MOD_ID, "geo/watcher.geo.json");
    }

    @Override
    public Identifier getTextureResource(WatcherEntity entity) {
        return Identifier.of(VoidWatcherMod.MOD_ID, "textures/entity/watcher.png");
    }

    @Override
    public Identifier getAnimationResource(WatcherEntity entity) {
        return Identifier.of(VoidWatcherMod.MOD_ID, "animations/watcher.animation.json");
    }
}
