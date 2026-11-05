package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** World ids ("minecraft:the_nether") and version helpers (Minecraft 1.21.x). */
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

    public static String blockId(BlockState state) {
        try {
            return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String itemId(ItemStack stack) {
        try {
            return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String entityTypeId(EntityType<?> type) {
        try {
            return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }

    public static void sendSuccess(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> text, false);
    }

    public static boolean isOnGround(Entity entity) {
        return entity.onGround();
    }
}
