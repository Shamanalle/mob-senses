package com.kasper.mobsenses.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A player's game language ("ru_ru"), across Minecraft 1.20 - 1.20.4 (1.20.1 field or 1.20.2+ ClientInformation). */
public final class PlayerLanguage {

    private static final Field LANGUAGE_FIELD = findField();
    private static final Method CLIENT_INFO_METHOD = LANGUAGE_FIELD == null
            ? findMethod(ServerPlayer.class, "clientInformation", "method_53823", "m_294126_")
            : null;
    private static final Method INFO_LANG_METHOD = CLIENT_INFO_METHOD != null
            ? findFirstStringNoArg(CLIENT_INFO_METHOD.getReturnType())
            : null;

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        try {
            if (LANGUAGE_FIELD != null) {
                Object v = LANGUAGE_FIELD.get(player);
                return v instanceof String ? (String) v : "";
            }
            if (CLIENT_INFO_METHOD != null && INFO_LANG_METHOD != null) {
                Object info = CLIENT_INFO_METHOD.invoke(player);
                if (info != null) {
                    Object v = INFO_LANG_METHOD.invoke(info);
                    return v instanceof String ? (String) v : "";
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
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

    private static Method findMethod(Class<?> cls, String... names) {
        for (String name : names) {
            try {
                return cls.getMethod(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findFirstStringNoArg(Class<?> cls) {
        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() == 0
                    && m.getReturnType() == String.class
                    && !"toString".equals(m.getName())) {
                return m;
            }
        }
        return null;
    }
}
