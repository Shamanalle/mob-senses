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
    private static final NoiseStats ALERTED = new NoiseStats();
    private static final NoiseCoalescer COALESCER = new NoiseCoalescer();
    private static final VoiceTracker VOICE = new VoiceTracker();
    private static final ScentModel SCENT = new ScentModel();
    private static final StealthHudState HUD = new StealthHudState();
    private static volatile ServerSettings settings = new ServerSettings(null);
    private static volatile ClientSettings clientSettings = new ClientSettings(null);
    private static volatile Platform platform = new Platform("unknown", "?");
    private static volatile boolean voiceChat;
    private static volatile boolean debug;

    private MobSenses() {
    }

    /**
     * The server starts: reads (or writes) the settings file in {@code configDir}.
     *
     * @param voiceChatInstalled whether Simple Voice Chat is present, for the status
     */
    public static void start(Path configDir, Platform where, boolean voiceChatInstalled) {
        platform = where;
        voiceChat = voiceChatInstalled || voiceChat;
        debug = false;
        COALESCER.clear();
        VOICE.clear();
        SCENT.clear();
        ServerSettings s = new ServerSettings(configDir.resolve(ServerSettings.FILE_NAME));
        s.load();
        settings = s;
        ModLog.info(NAME + " " + BuildInfo.version() + " on " + where + ", preset " + s.preset().id());
    }

    /**
     * Initializes the client HUD settings file ({@code config/mobsenses-client.properties}) and ensures
     * {@code config/mobsenses-server.properties} also exists on disk for singleplayer users who open the
     * {@code config/} folder before starting a world.
     */
    public static synchronized void initClient(Path configDir) {
        if (configDir == null) {
            return;
        }
        if (clientSettings.file() == null) {
            ClientSettings cs = new ClientSettings(configDir.resolve(ClientSettings.FILE_NAME));
            cs.load();
            clientSettings = cs;
        }
        if (settings.file() == null) {
            ServerSettings ss = new ServerSettings(configDir.resolve(ServerSettings.FILE_NAME));
            ss.load();
            settings = ss;
        }
    }

    /** Marks Simple Voice Chat as active when its plugin service initializes. */
    public static void markVoiceChatActive(boolean active) {
        voiceChat = active;
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

    /** Whether admins see debug particles for noises and alerted mobs. */
    public static boolean debug() {
        return debug;
    }

    public static void setDebug(boolean on) {
        debug = on;
    }

    public static NoiseStats noise() {
        return NOISE;
    }

    /** How many mobs heard a noise and went to investigate it. */
    public static NoiseStats alerted() {
        return ALERTED;
    }

    public static NoiseCoalescer coalescer() {
        return COALESCER;
    }

    public static VoiceTracker voice() {
        return VOICE;
    }

    public static ScentModel scent() {
        return SCENT;
    }

    public static StealthHudState hud() {
        return HUD;
    }

    public static ClientSettings clientSettings() {
        return clientSettings;
    }

    /**
     * Records a voice noise emitted by a speaking player at {@code (x, y, z)} in {@code world} with
     * adaptive base range {@code baseRange}.
     */
    public static void voiceEvent(String world, double x, double y, double z, double baseRange) {
        try {
            if (!settings.voice() || baseRange <= 0.0) {
                return;
            }
            Preset preset = settings.presetOf(world);
            if (preset == Preset.OFF) {
                return;
            }
            NOISE.record(System.currentTimeMillis());
            double range = NoiseModel.effectiveRange(baseRange, preset, settings.hearing());
            if (range >= NoiseModel.MIN_AUDIBLE_RANGE) {
                COALESCER.record(world, NoiseModel.Kind.VOICE, "mobsenses:voice", x, y, z, range);
            }
        } catch (Throwable t) {
            ModLog.failure("Handling a voice event", t);
        }
    }

    /**
     * A game event happened in a world (a step, a block broken, a door opened, an explosion...). Called on
     * the thread that owns the world; never throws.
     *
     * @param event the event's id, "minecraft:step"
     * @param world the world's id, "minecraft:overworld"
     */
    public static void gameEvent(String event, String world, double x, double y, double z) {
        gameEvent(event, world, x, y, z, NoiseModel.Posture.WALKING, null, null, true);
    }

    /**
     * A game event with its context: the player's posture, the block material under feet or acted upon,
     * the block id, and whether a player caused or was involved in the event.
     */
    public static void gameEvent(String event, String world, double x, double y, double z,
                                 NoiseModel.Posture posture, AcousticMaterial material,
                                 String blockId, boolean playerInvolved) {
        gameEvent(event, world, x, y, z, posture, material, blockId, playerInvolved, 1.0);
    }

    /**
     * A game event with its context and an optional environmental/gear multiplier (e.g. rain muffling
     * or Feather Falling boots).
     */
    public static void gameEvent(String event, String world, double x, double y, double z,
                                 NoiseModel.Posture posture, AcousticMaterial material,
                                 String blockId, boolean playerInvolved, double envMultiplier) {
        try {
            Preset preset = settings.presetOf(world);
            if (preset == Preset.OFF) {
                return;
            }
            NOISE.record(System.currentTimeMillis());
            NoiseModel.Kind kind = NoiseModel.classifyEvent(event);
            if (kind == null || (kind.requiresPlayerSource() && !playerInvolved)) {
                return;
            }
            double base = NoiseModel.baseRange(event, posture, material, blockId) * Math.max(0.0, envMultiplier);
            double range = NoiseModel.effectiveRange(base, preset, settings.hearing());
            if (range >= NoiseModel.MIN_AUDIBLE_RANGE) {
                COALESCER.record(world, kind, event, x, y, z, range);
                if (playerInvolved) {
                    HUD.onLocalNoise(range, System.currentTimeMillis());
                }
            }
        } catch (Throwable t) {
            ModLog.failure("Handling the game event " + event, t);
        }
    }
}
