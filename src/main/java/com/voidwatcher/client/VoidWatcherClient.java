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
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;

public class VoidWatcherClient implements ClientModInitializer {

    private static int scareTicks;
    private static int scareCooldown;
    private static int flicker;
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

        if (hardTrigger || (stareTrigger && client.world.getRandom().nextInt(120) == 0)
                || (nearestDistance < 4.5 && client.world.getRandom().nextInt(80) == 0)) {
            triggerScare(client, nearestDistance < 5.0);
        }
    }

    private static void triggerScare(MinecraftClient client, boolean violent) {
        scareTicks = violent ? 24 : 16;
        scareCooldown = violent ? 115 : 85;
        flicker = 0;

        if (client.world != null) {
            client.player.playSound(
                    violent ? SoundEvents.ENTITY_WARDEN_ROAR : SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    violent ? 1.25F : 0.85F,
                    violent ? 0.55F : 0.45F
            );

            scareText = switch (client.world.getRandom().nextInt(5)) {
                case 0 -> "НЕ ОБОРАЧИВАЙСЯ";
                case 1 -> "ОН СМОТРИТ НА ТЕБЯ";
                case 2 -> "БЕГИ";
                case 3 -> "ТЫ УЖЕ НЕ ОДИН";
                default -> "НЕ ДАЙ ЕМУ УВИДЕТЬ ТЕБЯ";
            };
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

        int alpha = Math.min(210, 70 + scareTicks * 7);
        if (((flicker / 2) & 1) == 0) {
            context.fill(0, 0, width, height, (alpha << 24) | 0x300000);
        } else {
            context.fill(0, 0, width, height, (alpha << 24) | 0x000000);
        }

        int edge = Math.min(150, 35 + scareTicks * 5);
        context.fill(0, 0, width, 16, (edge << 24) | 0x700000);
        context.fill(0, height - 16, width, height, (edge << 24) | 0x700000);
        context.fill(0, 0, 16, height, (edge << 24) | 0x700000);
        context.fill(width - 16, 0, width, height, (edge << 24) | 0x700000);

        int eyeWidth = Math.min(width / 2, 280);
        int eyeHeight = 18 + Math.min(28, scareTicks);
        int left = centerX - eyeWidth / 2;
        int right = centerX + eyeWidth / 2;

        context.fill(left, centerY - eyeHeight / 2, right, centerY + eyeHeight / 2, 0xFFF1F1F1);
        context.fill(centerX - 32, centerY - 58, centerX + 32, centerY + 58, 0xFF090909);
        context.fill(centerX - 10, centerY - 23, centerX + 10, centerY + 23, 0xFF8A0000);

        for (int i = 0; i < 10; i++) {
            int y = centerY - 70 + i * 14;
            context.fill(left - 30 - i * 3, y, left + 25 + i * 4, y + 2, 0x70FFFFFF);
            context.fill(right - 25 - i * 4, y + 5, right + 30 + i * 3, y + 7, 0x60FFFFFF);
        }

        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(scareText).formatted(Formatting.DARK_RED, Formatting.BOLD),
                centerX,
                centerY + 92,
                0xFFFFFFFF
        );
    }
}
