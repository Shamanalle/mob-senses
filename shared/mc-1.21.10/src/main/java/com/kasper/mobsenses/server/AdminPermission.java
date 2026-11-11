package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Who may use {@code /mobsenses} on Minecraft 1.16.5 - 1.21.10: console, command blocks, and op level 2+. */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer) || source.hasPermission(2);
    }
}
