package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/** World ids ("minecraft:the_nether"), as the settings file names them (Minecraft 1.21.x). */
public final class Worlds {

    public static final TagKey<EntityType<?>> LISTENERS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(MobSenses.MOD_ID, "listeners"));

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

    public static boolean isTaggedListener(EntityType<?> type) {
        return type != null && type.is(LISTENERS);
    }
}
