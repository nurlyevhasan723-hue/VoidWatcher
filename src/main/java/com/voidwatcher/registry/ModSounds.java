package com.voidwatcher.registry;

import com.voidwatcher.VoidWatcherMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {

    public static final SoundEvent VOICE_NO_TURN = register("voice_no_turn");
    public static final SoundEvent VOICE_WATCHES = register("voice_watches");
    public static final SoundEvent VOICE_RUN = register("voice_run");
    public static final SoundEvent VOICE_ALONE = register("voice_alone");
    public static final SoundEvent VOICE_DONT_SEE = register("voice_dont_see");
    public static final SoundEvent VOICE_NEAR = register("voice_near");
    public static final SoundEvent VOICE_BREATHING = register("voice_breathing");
    public static final SoundEvent VOICE_HEARS = register("voice_hears");

    private ModSounds() {
    }

    private static SoundEvent register(String id) {
        Identifier identifier = Identifier.of(VoidWatcherMod.MOD_ID, id);
        return Registry.register(Registries.SOUND_EVENT, identifier, SoundEvent.of(identifier));
    }

    public static SoundEvent getVoice(int line) {
        return switch (line) {
            case 0 -> VOICE_NO_TURN;
            case 1 -> VOICE_WATCHES;
            case 2 -> VOICE_RUN;
            case 3 -> VOICE_ALONE;
            case 4 -> VOICE_DONT_SEE;
            case 5 -> VOICE_NEAR;
            case 6 -> VOICE_BREATHING;
            default -> VOICE_HEARS;
        };
    }

    public static void register() {
    }
}
