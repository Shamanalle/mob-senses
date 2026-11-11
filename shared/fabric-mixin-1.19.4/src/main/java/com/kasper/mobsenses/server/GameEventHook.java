package com.kasper.mobsenses.server;

import com.kasper.mobsenses.ModLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Hands a world's game event to the shared code (Minecraft 1.19.3 - 1.20.4). */
public final class GameEventHook {

    private GameEventHook() {
    }

    public static void heard(ServerLevel level, GameEvent event, Vec3 pos, GameEvent.Context context) {
        try {
            String id = String.valueOf(BuiltInRegistries.GAME_EVENT.getKey(event));
            ServerHearing.onGameEvent(
                    level,
                    id,
                    pos.x, pos.y, pos.z,
                    context == null ? null : context.sourceEntity(),
                    context == null ? null : context.affectedState());
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
