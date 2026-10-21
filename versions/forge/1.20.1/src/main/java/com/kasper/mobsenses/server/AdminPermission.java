package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;

/** Who may use {@code /mobsenses}: the console and command blocks, and operators of level 2 or more. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
