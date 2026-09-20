package com.voidwatcher.client;

import com.voidwatcher.client.render.WatcherRenderer;
import com.voidwatcher.entity.WatcherEntity;
import com.voidwatcher.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;

public class VoidWatcherClient implements ClientModInitializer {

    private static int scareTicks;
    private static int scareCooldown;
    private static int flicker;
    private static int scareType;
    private static String scareText = "НЕ ОБОРАЧИВАЙСЯ";

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(VoidWatcherClient::tick);
        HudRenderCallback.EVENT.register(VoidWatcherClient::renderScare);
    }

    private static void tick(MinecraftClient client) {
        if (scareCooldown > 0) {
            scareCooldown--;
        }
        if (scareTicks > 0) {
            scareTicks--;
            flicker++;
        }

        if (client.world == null || client.player == null) {
            return;
        }

        Box box = client.player.getBoundingBox().expand(18.0);
        List<WatcherEntity> watchers = client.world.getEntitiesByType(
                ModEntities.WATCHER,
                box,
                entity -> entity.isAlive()
        );

        WatcherEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (WatcherEntity watcher : watchers) {
            double distance = client.player.distanceTo(watcher);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = watcher;
            }
        }

        if (nearest == null || scareCooldown > 0) {
            return;
        }

        boolean hardTrigger = nearestDistance < 4.5 && nearest.isAttackingAnimation();
        boolean stareTrigger = nearestDistance < 10.0 && nearest.isStaring();

        if (hardTrigger || (stareTrigger && client.world.getRandom().nextInt(90) == 0)
                || (nearestDistance < 4.5 && client.world.getRandom().nextInt(55) == 0)) {
            triggerScare(client, nearestDistance < 5.0);
        }
    }

    private static void triggerScare(MinecraftClient client, boolean violent) {
        scareTicks = violent ? 30 : 20;
        scareCooldown = violent ? 100 : 70;
        flicker = 0;
        scareType = client.world.getRandom().nextInt(6);

        scareText = switch (scareType) {
            case 0 -> "НЕ ОБОРАЧИВАЙСЯ";
            case 1 -> "ОН СМОТРИТ НА ТЕБЯ";
            case 2 -> "БЕГИ";
            case 3 -> "ТЫ УЖЕ НЕ ОДИН";
            case 4 -> "ОН УЖЕ РЯДОМ";
            default -> "ОН СЛЫШИТ ТЕБЯ";
        };

        if (client.world != null) {
            client.player.playSound(
                    violent ? SoundEvents.ENTITY_WARDEN_ROAR : SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    violent ? 1.35F : 0.90F,
                    violent ? 0.50F : 0.42F
            );
        }

        client.player.sendMessage(
                Text.literal("[VOID] " + scareText).formatted(Formatting.DARK_RED, Formatting.BOLD),
                true
        );
    }

    private static void renderScare(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || scareTicks <= 0) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int alpha = Math.min(220, 65 + scareTicks * 6);

        switch (scareType) {
            case 0 -> renderEyes(context, width, height, centerX, centerY, alpha);
            case 1 -> renderStatic(context, width, height, alpha);
            case 2 -> renderBlood(context, width, height, alpha);
            case 3 -> renderMouth(context, width, height, centerX, centerY, alpha);
            case 4 -> renderVoid(context, width, height, centerX, centerY, alpha);
            default -> renderGlitch(context, width, height, alpha);
        }

        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(scareText).formatted(Formatting.DARK_RED, Formatting.BOLD),
                centerX,
                height - 42,
                0xFFFFFFFF
        );
    }

    private static void renderEyes(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x130000);
        int eyeWidth = Math.min(width / 2, 280);
        context.fill(cx - eyeWidth / 2, cy - 20, cx - 20, cy + 20, 0xFFE6E6E0);
        context.fill(cx + 20, cy - 20, cx + eyeWidth / 2, cy + 20, 0xFFE6E6E0);
        context.fill(cx - 7, cy - 12, cx + 7, cy + 12, 0xFF5A0000);
        context.fill(cx - 45, cy - 50, cx - 20, cy + 50, 0xFF050505);
        context.fill(cx + 20, cy - 50, cx + 45, cy + 50, 0xFF050505);
    }

    private static void renderStatic(DrawContext context, int width, int height, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x000000);
        for (int y = 0; y < height; y += 6) {
            int wobble = ((flicker * 13 + y * 7) % 19) - 9;
            context.fill(0, y, width, y + 2, ((90 + alpha / 2) << 24) | 0xFFFFFF);
            context.fill(Math.max(0, wobble), y + 3, Math.min(width, width / 2 + wobble), y + 4, 0x60FFFFFF);
        }
    }

    private static void renderBlood(DrawContext context, int width, int height, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x180000);
        int edge = Math.min(190, alpha + 20);
        context.fill(0, 0, width, 22, (edge << 24) | 0x850000);
        context.fill(0, height - 22, width, height, (edge << 24) | 0x850000);
        context.fill(0, 0, 22, height, (edge << 24) | 0x850000);
        context.fill(width - 22, 0, width, height, (edge << 24) | 0x850000);
        for (int i = 0; i < 10; i++) {
            int x = (i * 137 + flicker * 11) % Math.max(1, width);
            int y = (i * 83 + flicker * 17) % Math.max(1, height);
            context.fill(x, y, x + 3, Math.min(height, y + 20 + i * 3), 0xAA7A0000);
        }
    }

    private static void renderMouth(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x040404);
        int mw = Math.min(width - 80, 460);
        int mh = 90 + (flicker % 20);
        context.fill(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2, 0xEE050505);
        for (int x = cx - mw / 2 + 10; x < cx + mw / 2 - 10; x += 24) {
            context.fill(x, cy - mh / 2, x + 11, cy - mh / 2 + 38, 0xFFE6E0D2);
        }
    }

    private static void renderVoid(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x000000);
        int r = Math.min(160, 60 + scareTicks * 4);
        context.fill(cx - r, cy - r, cx + r, cy + r, 0xE8090909);
        context.fill(cx - 12, cy - 75, cx + 12, cy + 75, 0xFFE00000);
        context.fill(cx - 75, cy - 12, cx + 75, cy + 12, 0xFFE00000);
    }

    private static void renderGlitch(DrawContext context, int width, int height, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x090909);
        for (int i = 0; i < 24; i++) {
            int y = (i * 37 + flicker * 9) % Math.max(1, height);
            int x = (i * 71 + flicker * 13) % Math.max(1, width);
            int w = 15 + ((i * 17) % Math.max(20, width / 4));
            context.fill(x, y, Math.min(width, x + w), y + 3, 0xAAFFFFFF);
            if ((i & 1) == 0) {
                context.fill(Math.max(0, x - 22), y + 5, Math.min(width, x + w + 18), y + 7, 0x88900000);
            }
        }
    }
}
