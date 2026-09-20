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

        Box box = client.player.getBoundingBox().expand(24.0);
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

        // The Watcher creates a cold mist / static feel even before the hard scare.
        if (nearest != null && nearestDistance < 15.0 && scareCooldown <= 0
                && client.world.getRandom().nextInt(180) == 0) {
            scareText = "ВОЗДУХ СТАЛ СЛИШКОМ ТИХИМ";
            scareType = 5;
            scareTicks = 8;
            flicker = 0;
        }

        if (nearest == null || scareCooldown > 0) {
            return;
        }

        boolean hardTrigger = nearestDistance < 4.5 && nearest.isAttackingAnimation();
        boolean talkingTrigger = nearestDistance < 12.0 && nearest.isSpeaking();
        boolean stareTrigger = nearestDistance < 10.0 && nearest.isStaring();

        if (hardTrigger || talkingTrigger
                || (stareTrigger && client.world.getRandom().nextInt(75) == 0)
                || (nearestDistance < 4.5 && client.world.getRandom().nextInt(45) == 0)) {
            triggerScare(client, nearestDistance < 5.0);
        }
    }

    private static void triggerScare(MinecraftClient client, boolean violent) {
        scareTicks = violent ? 34 : 23;
        scareCooldown = violent ? 110 : 72;
        flicker = 0;
        scareType = client.world.getRandom().nextInt(7);

        scareText = switch (scareType) {
            case 0 -> "НЕ ОБОРАЧИВАЙСЯ";
            case 1 -> "ОН СМОТРИТ НА ТЕБЯ";
            case 2 -> "БЕГИ";
            case 3 -> "ТЫ УЖЕ НЕ ОДИН";
            case 4 -> "ОН УЖЕ РЯДОМ";
            case 5 -> "ОН ДЫШИТ ЗА ТВОЕЙ СПИНОЙ";
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
        if (client.world == null || client.player == null) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        if (scareTicks > 0) {
            int alpha = Math.min(230, 70 + scareTicks * 6);

            switch (scareType) {
                case 0 -> renderEyes(context, width, height, centerX, centerY, alpha);
                case 1 -> renderStatic(context, width, height, alpha);
                case 2 -> renderBlood(context, width, height, alpha);
                case 3 -> renderMouth(context, width, height, centerX, centerY, alpha);
                case 4 -> renderVoid(context, width, height, centerX, centerY, alpha);
                case 5 -> renderFace(context, width, height, centerX, centerY, alpha);
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

        if (nearestWatcherNear(client)) {
            renderColdMist(context, width, height);
        }
    }

    private static boolean nearestWatcherNear(MinecraftClient client) {
        Box box = client.player.getBoundingBox().expand(20.0);
        return !client.world.getEntitiesByType(
                ModEntities.WATCHER,
                box,
                entity -> entity.isAlive()
        ).isEmpty();
    }

    private static void renderColdMist(DrawContext context, int width, int height) {
        int alpha = 35 + (flicker % 8) * 3;
        context.fill(0, 0, width, height, (alpha << 24) | 0xDDE5E8);

        for (int i = 0; i < 12; i++) {
            int y = (i * 71 + flicker * 3) % Math.max(1, height);
            int x = (i * 113 + flicker * 5) % Math.max(1, width);
            int w = 120 + ((i * 31) % Math.max(60, width / 3));
            context.fill(x, y, Math.min(width, x + w), y + 10, 0x14000000);
        }
    }

    private static void renderFace(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x080808);
        int faceW = Math.min(width - 20, 620);
        int faceH = Math.min(height - 30, 520);
        int left = cx - faceW / 2;
        int top = cy - faceH / 2;

        context.fill(left, top, left + faceW, top + faceH, 0xE90B0B0D);
        context.fill(left + 65, top + 80, left + faceW - 65, top + 125, 0xFFDDDDD6);
        context.fill(left + 110, top + faceH - 170, left + faceW - 110, top + faceH - 75, 0xFF020203);

        for (int x = left + 85; x < left + faceW - 85; x += 30) {
            context.fill(x, top + faceH - 170, x + 13, top + faceH - 116, 0xFFE5E0D3);
        }

        context.fill(cx - 15, top + 85, cx + 15, top + 120, 0xFF6D0000);
        context.fill(cx - 9, top + faceH - 155, cx + 9, top + faceH - 90, 0xFF7A0000);

        for (int i = 0; i < 14; i++) {
            int y = top + 15 + i * 34;
            int x = left + 20 + (i * 41 + flicker * 9) % Math.max(1, faceW - 80);
            context.fill(x, y, Math.min(left + faceW - 10, x + 24 + i * 3), y + 3, 0x50B0B0B0);
        }
    }

    private static void renderEyes(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x130000);
        int eyeWidth = Math.min(width / 2, 300);
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
        int mw = Math.min(width - 80, 520);
        int mh = 90 + (flicker % 20);
        context.fill(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2, 0xEE050505);
        for (int x = cx - mw / 2 + 10; x < cx + mw / 2 - 10; x += 24) {
            context.fill(x, cy - mh / 2, x + 11, cy - mh / 2 + 38, 0xFFE6E0D2);
        }
    }

    private static void renderVoid(DrawContext context, int width, int height, int cx, int cy, int alpha) {
        context.fill(0, 0, width, height, (alpha << 24) | 0x000000);
        int r = Math.min(180, 70 + scareTicks * 4);
        context.fill(cx - r, cy - r, cx + r, cy + r, 0xE8090909);
        context.fill(cx - 12, cy - 80, cx + 12, cy + 80, 0xFFE00000);
        context.fill(cx - 80, cy - 12, cx + 80, cy + 12, 0xFFE00000);
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
