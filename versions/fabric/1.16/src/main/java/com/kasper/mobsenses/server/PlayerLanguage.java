package com.kasper.mobsenses.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/** A player's game language ("ru_ru"), as their client reported it (Minecraft 1.16.5 - 1.20.1). */
public final class PlayerLanguage {

    private static final Field LANGUAGE = findField();

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        if (LANGUAGE == null) {
            return "";
        }
        try {
            Object v = LANGUAGE.get(player);
            return v instanceof String ? (String) v : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static Field findField() {
        for (Field f : ServerPlayer.class.getDeclaredFields()) {
            if (f.getType() == String.class) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
