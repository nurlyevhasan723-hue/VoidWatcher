package com.voidwatcher.client.render;

import com.voidwatcher.VoidWatcherMod;
import com.voidwatcher.entity.WatcherEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WatcherRenderer extends GeoEntityRenderer<WatcherEntity> {

    public WatcherRenderer(EntityRendererFactory.Context context) {
        super(context, new WatcherModel());
        this.shadowRadius = 0.75F;
    }

    @Override
    public Identifier getTextureLocation(WatcherEntity entity) {
        return Identifier.of(VoidWatcherMod.MOD_ID, "textures/entity/watcher.png");
    }
}
