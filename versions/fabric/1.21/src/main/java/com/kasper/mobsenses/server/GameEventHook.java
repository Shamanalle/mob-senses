package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Hands a world's game event to the shared code (Minecraft 1.21.x). */
public final class GameEventHook {

    private GameEventHook() {
    }

    public static void heard(ServerLevel level, Holder<GameEvent> event, Vec3 pos) {
        try {
            String id = event.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            MobSenses.gameEvent(id, Worlds.id(level), pos.x, pos.y, pos.z);
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
