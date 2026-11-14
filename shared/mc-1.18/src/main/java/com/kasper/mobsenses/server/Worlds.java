package com.kasper.mobsenses.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** World ids ("minecraft:the_nether") and version helpers (Minecraft 1.16.5 - 1.18.2). */
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

    public static boolean isTaggedListener(EntityType<?> type) {
        return false;
    }

    public static boolean isTaggedBoss(EntityType<?> type) {
        return false;
    }

    public static String blockId(BlockState state) {
        try {
            return String.valueOf(Registry.BLOCK.getKey(state.getBlock()));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String itemId(ItemStack stack) {
        try {
            return String.valueOf(Registry.ITEM.getKey(stack.getItem()));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String entityTypeId(EntityType<?> type) {
        try {
            return String.valueOf(Registry.ENTITY_TYPE.getKey(type));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    public static MutableComponent translatable(String key) {
        return new TranslatableComponent(key);
    }

    public static void sendSuccess(CommandSourceStack source, Component text) {
        source.sendSuccess(text, false);
    }

    public static boolean isOnGround(Entity entity) {
        return entity.isOnGround();
    }
}
