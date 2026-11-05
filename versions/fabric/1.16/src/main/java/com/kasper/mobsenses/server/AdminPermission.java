package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;

/** Who may use {@code /mobsenses} on 1.16.5 - 1.20.4: the console and command blocks, and operators of level 2+. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
