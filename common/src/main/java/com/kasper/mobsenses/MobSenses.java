package com.kasper.mobsenses;

import java.nio.file.Path;

/**
 * The server side of Mob Senses, shared by every loader and the Bukkit plugin. The platform calls
 * {@link #start} when the server starts and hands its game events to {@link #gameEvent}; everything
 * that does not need Minecraft's own classes is decided here.
 */
public final class MobSenses {

    public static final String MOD_ID = "mobsenses";
    public static final String NAME = "Mob Senses";

    /** What the platform says about itself, for the status line: "Fabric 1.21.1", "Paper 1.20.1". */
    @com.github.bsideup.jabel.Desugar
    public record Platform(String loader, String minecraft) {
        @Override
        public String toString() {
            return loader + " " + minecraft;
        }
    }

    private static final NoiseStats NOISE = new NoiseStats();
    private static volatile ServerSettings settings = new ServerSettings(null);
    private static volatile Platform platform = new Platform("unknown", "?");
    private static volatile boolean voiceChat;

    private MobSenses() {
    }

    /**
     * The server starts: reads (or writes) the settings file in {@code configDir}.
     *
     * @param voiceChatInstalled whether Simple Voice Chat is present, for the status
     */
    public static void start(Path configDir, Platform where, boolean voiceChatInstalled) {
        platform = where;
        voiceChat = voiceChatInstalled;
        ServerSettings s = new ServerSettings(configDir.resolve(ServerSettings.FILE_NAME));
        s.load();
        settings = s;
        ModLog.info(NAME + " " + BuildInfo.version() + " on " + where + ", preset " + s.preset().id());
    }

    /** How often the settings file is checked for an admin's edits. */
    private static final int RELOAD_CHECK_TICKS = 40;
    private static int ticks;

    /** The end of a server tick, on the server's main thread (on Folia, the global region's). Never throws. */
    public static void serverTick() {
        try {
            if (++ticks % RELOAD_CHECK_TICKS == 0 && settings.reloadIfChanged()) {
                ModLog.info("Read " + ServerSettings.FILE_NAME + " again: preset " + settings.preset().id());
            }
        } catch (Throwable t) {
            ModLog.failure("The server tick", t);
        }
    }

    public static ServerSettings settings() {
        return settings;
    }

    public static Platform platform() {
        return platform;
    }

    public static boolean voiceChatInstalled() {
        return voiceChat;
    }

    public static NoiseStats noise() {
        return NOISE;
    }

    /**
     * A game event happened in a world (a step, a block broken, a door opened, an explosion...). Called on
     * the thread that owns the world; never throws.
     *
     * @param event the event's id, "minecraft:step"
     * @param world the world's id, "minecraft:overworld"
     */
    public static void gameEvent(String event, String world, double x, double y, double z) {
        try {
            if (settings.presetOf(world) == Preset.OFF) {
                return;
            }
            NOISE.record(System.currentTimeMillis());
        } catch (Throwable t) {
            ModLog.failure("Handling the game event " + event, t);
        }
    }
}
