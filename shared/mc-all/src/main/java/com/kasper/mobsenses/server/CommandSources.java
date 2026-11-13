package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Cross-version helper for creating a {@link CommandSourceStack} from a {@link ServerPlayer}.
 * <p>
 * In Minecraft 1.21.2+, Mojang added a player-specific {@code createCommandSourceStack()}
 * overload on {@code ServerPlayer} (Intermediary {@code method_64396}) replacing the legacy
 * zero-arg method on {@code Entity} ({@code method_5671}). Resolving it reflectively lets
 * a single compiled {@code 1.21} band run across {@code 1.21} through {@code 1.21.11}
 * without {@link NoSuchMethodError}.
 */
public final class CommandSources {

    private static final String[] METHOD_NAMES = {
            "createCommandSourceStack",
            "method_64396",
            "method_5671",
            "m_20203_",
            "func_195051_bN"
    };

    private static final Method CREATE_SOURCE = findCreateSource();

    private CommandSources() {
    }

    /** Returns the command source stack for {@code player}, or {@code null} if unavailable. */
    public static CommandSourceStack of(ServerPlayer player) {
        if (player == null || CREATE_SOURCE == null) {
            return null;
        }
        try {
            Object result = CREATE_SOURCE.invoke(player);
            return result instanceof CommandSourceStack ? (CommandSourceStack) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Returns {@code true} if {@code player} is an operator / singleplayer host admin. */
    public static boolean isAdmin(ServerPlayer player) {
        CommandSourceStack source = of(player);
        return source != null && AdminPermission.isAdmin(source);
    }

    private static Method findCreateSource() {
        for (String name : METHOD_NAMES) {
            try {
                Method m = ServerPlayer.class.getMethod(name);
                if (isMatchingMethod(m)) {
                    return m;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Class<?> c = ServerPlayer.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : METHOD_NAMES) {
                try {
                    Method m = c.getDeclaredMethod(name);
                    if (!isMatchingMethod(m)) {
                        continue;
                    }
                    if (!Modifier.isPublic(m.getModifiers())) {
                        m.setAccessible(true);
                    }
                    return m;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isMatchingMethod(Method m) {
        return m.getParameterTypes().length == 0
                && CommandSourceStack.class.isAssignableFrom(m.getReturnType());
    }
}
