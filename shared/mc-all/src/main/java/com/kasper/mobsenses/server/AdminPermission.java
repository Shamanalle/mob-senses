package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/** Who may use {@code /mobsenses} on Minecraft 1.21.11 - 26.x: console, command blocks, and game masters (op 2+). */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getPlayer() == null || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
