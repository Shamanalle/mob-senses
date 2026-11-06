package com.kasper.mobsenses.gametest;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Worlds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** What the in-game test does through the client on 1.21.x. */
final class TestScreens {

    private static volatile java.util.concurrent.CompletableFuture<?> reload;
    private static final List<Mob> SCENE_MOBS = new ArrayList<>();

    private TestScreens() {
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    static void guiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    /** A world where the player may use commands (cheats on), so /mobsenses works. */
    static void allowCommands(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState settings) {
        settings.setAllowCommands(true);
    }

    /** Sends a command as if typed in chat, without the slash. */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }

    static void clearChat(Minecraft client) {
        client.gui.getChat().clearMessages(false);
    }

    /** Whether the game has finished loading resources. */
    static boolean loaded(Minecraft client) {
        return reload == null || reload.isDone();
    }

    /**
     * Builds a stone-brick test arena around (0, 120, 0), teleports the player to watch from (0, 121, -5.5),
     * and spawns listener mobs on the arena floor.
     */
    static void setupInvestigationScene(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(18000L);
            SCENE_MOBS.clear();
            int baseY = 120;
            for (int dx = -14; dx <= 14; dx++) {
                for (int dz = -14; dz <= 14; dz++) {
                    for (int dy = 1; dy <= 7; dy++) {
                        level.setBlockAndUpdate(new BlockPos(dx, baseY + dy, dz), Blocks.AIR.defaultBlockState());
                    }
                    level.setBlockAndUpdate(new BlockPos(dx, baseY, dz), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(new BlockPos(0, baseY + 1, 2), Blocks.BELL.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(-4, baseY + 1, 0), Blocks.LANTERN.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(4, baseY + 1, 0), Blocks.LANTERN.defaultBlockState());

            for (ServerPlayer player : level.players()) {
                player.teleportTo(level, 0.5, baseY + 1.0, -5.5, java.util.Collections.emptySet(), 0.0f, 14.0f, false);
            }

            double[][] positions = {{-6.5, 6.5}, {6.5, 6.5}, {0.5, 9.5}};
            for (double[] pos : positions) {
                Mob husk = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                if (husk != null) {
                    husk.setPos(pos[0], baseY + 1.0, pos[1]);
                    husk.setPersistenceRequired();
                    level.addFreshEntity(husk);
                    SCENE_MOBS.add(husk);
                }
            }
        });
    }

    /** Emits a loud bell noise at (0.5, 121.0, 2.5) so nearby listener mobs walk to investigate it. */
    static void ringSceneBell(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = level.players().isEmpty() ? null : level.players().get(0);
            ServerHearing.onGameEvent(level, "minecraft:bell_ring", 0.5, 121.0, 2.5, player, Blocks.BELL.defaultBlockState());
        });
    }

    /**
     * Exercises Simple Voice Chat audio, held-torch sight, and wounded blood-scent while lighting up
     * all three bars (Noise, Light, Scent) + Voice indicator on the client Stealth HUD.
     */
    static void triggerVoiceSightAndScent(Minecraft client) {
        long now = System.currentTimeMillis();
        short[] voiceFrame = new short[]{14000, -14000, 14000, -14000, 14000, -14000};
        if (client.player != null) {
            client.player.setHealth(6.0f);
            client.player.hurtTime = 0;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            String worldId = Worlds.id(level);
            for (ServerPlayer player : level.players()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
                player.setHealth(6.0f);
                MobSenses.voice().onVoiceFrame(player.getUUID(), voiceFrame, false, now);
                MobSenses.scent().recordPlayer(worldId, player.getUUID(), -0.7, 121.0, -3.4, 6.0, 20.0, Preset.DEFAULT, now - 6000L);
                MobSenses.scent().recordPlayer(worldId, player.getUUID(), 0.9, 121.0, -2.1, 6.0, 20.0, Preset.DEFAULT, now - 3000L);
                MobSenses.scent().recordPlayer(worldId, player.getUUID(), 0.2, 121.0, -0.7, 6.0, 20.0, Preset.DEFAULT, now - 1000L);
                ServerHearing.tickPlayerSightAndScent(level, worldId, player, Preset.DEFAULT, now);
            }
        });
    }

    static void refreshVoiceAndNoiseForHud(Minecraft client) {
        long now = System.currentTimeMillis();
        short[] voiceFrame = new short[]{14000, -14000, 14000, -14000, 14000, -14000};
        MobSenses.voice().onClientSound(voiceFrame, false, now);
        MobSenses.hud().onLocalNoise(22.0, now);
        if (client.player != null) {
            client.player.hurtTime = 0;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerLevel level = server.overworld();
                com.kasper.mobsenses.server.ParticleSender.sendBloodCrumb(level, -0.7, 121.0, -3.4);
                com.kasper.mobsenses.server.ParticleSender.sendBloodCrumb(level, 0.9, 121.0, -2.1);
                com.kasper.mobsenses.server.ParticleSender.sendBloodCrumb(level, 0.2, 121.0, -0.7);
            });
        }
    }

    static void restorePlayerState(Minecraft client) {
        MobSenses.scent().clear();
        if (client.player != null) {
            client.player.setHealth(20.0f);
            client.player.hurtTime = 0;
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (ServerPlayer player : level.players()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setHealth(20.0f);
            }
        });
    }

    /** Verifies that at least one mob was alerted and moved closer to the bell at (0.5, 121.0, 2.5). */
    static void verifyInvestigation() {
        if (MobSenses.alerted().total() <= 0) {
            throw new AssertionError("Expected at least one listener mob to be alerted by noise, got 0");
        }
        boolean anyMovedCloser = false;
        for (Mob mob : SCENE_MOBS) {
            double dx = mob.getX() - 0.5;
            double dz = mob.getZ() - 2.5;
            double dist = Math.hypot(dx, dz);
            // Initial distances are ~7.2, ~7.2, and 7.0 blocks
            if (dist < 6.4) {
                anyMovedCloser = true;
                break;
            }
        }
        if (!anyMovedCloser) {
            throw new AssertionError("Alerted listener mobs did not walk toward the noise source");
        }
    }
}
