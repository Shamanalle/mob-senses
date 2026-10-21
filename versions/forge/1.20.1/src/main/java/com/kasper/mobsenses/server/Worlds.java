package com.kasper.mobsenses.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** World ids ("minecraft:the_nether"), as the settings file names them (Minecraft 1.20 - 1.21.x). */
public final class Worlds {

    private Worlds() {
    }

    public static String id(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static List<String> ids(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(id(level));
        }
        return out;
    }
}
