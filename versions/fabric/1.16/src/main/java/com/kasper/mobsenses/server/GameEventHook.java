package com.kasper.mobsenses.server;

import com.kasper.mobsenses.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Hands a world explosion to the shared code (Minecraft 1.16.5). */
public final class GameEventHook {

    private GameEventHook() {
    }

    public static void exploded(ServerLevel level, Entity source, double x, double y, double z) {
        try {
            ServerHearing.onGameEvent(level, "minecraft:explode", x, y, z, source, null);
        } catch (Throwable t) {
            ModLog.failure("Reading an explosion event", t);
        }
    }
}
