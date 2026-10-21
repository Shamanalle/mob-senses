package com.kasper.mobsenses.server;

import com.kasper.mobsenses.OpsFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may use {@code /mobsenses} on 1.21.x: the console and command blocks, and operators of level 2 or
 * more. {@code CommandSourceStack.hasPermission(int)} is gone from 1.21.11, which moved to permission
 * sets; there the level comes from {@code ops.json}, and in singleplayer the player owns the world.
 */
public final class AdminPermission {

    private static final OpsFile OPS = new OpsFile(FabricLoader.getInstance().getGameDir().resolve("ops.json"));
    private static boolean legacyMissing;

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }
        if (!legacyMissing) {
            try {
                return source.hasPermission(2);
            } catch (NoSuchMethodError e) {
                legacyMissing = true;
            }
        }
        return OPS.level(player.getUUID()) >= 2 || !source.getServer().isDedicatedServer();
    }
}
