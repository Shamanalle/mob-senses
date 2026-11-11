package com.kasper.mobsenses.server;

import com.kasper.mobsenses.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;

/** Hands a world's game event to the shared code (Minecraft 1.17 - 1.18.2). */
public final class GameEventHook {

    private static volatile boolean gameEventActive;

    private GameEventHook() {
    }

    public static void heard(ServerLevel level, Entity source, GameEvent event, BlockPos pos) {
        try {
            gameEventActive = true;
            String id = String.valueOf(Registry.GAME_EVENT.getKey(event));
            ServerHearing.onGameEvent(
                    level,
                    id,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    source,
                    level.getBlockState(pos));
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }

    public static void exploded(ServerLevel level, Entity source, double x, double y, double z) {
        if (gameEventActive) {
            return;
        }
        try {
            ServerHearing.onGameEvent(level, "minecraft:explode", x, y, z, source, null);
        } catch (Throwable t) {
            ModLog.failure("Reading an explosion event", t);
        }
    }
}
