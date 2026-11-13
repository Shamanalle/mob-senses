package com.kasper.mobsenses;

import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Client-side live synchronization state with a remote Mob Senses server or Bukkit/Paper/Folia plugin.
 * <p>
 * Accessed via {@link MobSenses#serverLink()}. When offline, in singleplayer without remote packets,
 * or connected to a vanilla server without Mob Senses, {@link #connected()} is {@code false} and
 * {@link #allowClientHud()} defaults to {@code true}. Once a {@code mobsenses:state} packet arrives,
 * {@link #remoteSettings()} mirrors the server's live configuration, {@link #allowClientHud()} enforces
 * the server's fair-play HUD lock, and {@link #isAdmin()} unlocks live remote editing in the GUI via
 * {@code mobsenses:admin}.
 */
public final class ServerLink {

    private volatile ServerSettings remoteSettings = new ServerSettings(null);
    private volatile boolean connected;
    private volatile boolean allowClientHud = true;
    private volatile boolean admin;
    private volatile boolean debug;
    private volatile boolean voiceInstalled;
    private volatile Preset activePreset = Preset.DEFAULT;
    private volatile Preset serverPreset = Preset.DEFAULT;
    private volatile String currentWorld = "";
    private volatile String serverPlatform = "";
    private volatile long noiseLastMinute;
    private volatile long alertedLastMinute;
    private volatile String worldsSummary = "";
    private volatile int playerAlert;

    private volatile boolean lastReplyOk = true;
    private volatile String lastReplyMessage = "";
    private volatile long lastReplyTimestampMs;
    private volatile int adminReplyCount;

    private volatile Consumer<Properties> adminSender;

    public boolean connected() {
        return connected;
    }

    /**
     * Whether the client Stealth & Noise HUD is permitted on the current server.
     * Always {@code true} in singleplayer or on servers without Mob Senses; reflects the server's
     * {@code allow_client_hud} setting when connected to a Mob Senses server.
     */
    public boolean allowClientHud() {
        return !connected || allowClientHud;
    }

    /** Whether the connected player has server admin permission to edit server settings from the GUI. */
    public boolean isAdmin() {
        return connected && admin;
    }

    /**
     * Effective {@link Preset} at the player's current world when connected to a Mob Senses server,
     * or {@link MobSenses#settings()}'s preset when offline/singleplayer.
     */
    public Preset activePreset() {
        return connected ? activePreset : MobSenses.settings().preset();
    }

    /** Global server preset when connected, or local {@link MobSenses#settings()}'s preset otherwise. */
    public Preset serverPreset() {
        return connected ? serverPreset : MobSenses.settings().preset();
    }

    public String currentWorld() {
        return connected ? currentWorld : "";
    }

    /** Remote server platform description (e.g. {@code "Paper 1.21.4"}), or {@code ""} when disconnected. */
    public String serverPlatform() {
        return connected ? serverPlatform : "";
    }

    public boolean debugEnabled() {
        return connected ? debug : MobSenses.debug();
    }

    public boolean voiceInstalled() {
        return connected ? voiceInstalled : MobSenses.voiceChatInstalled();
    }

    public long noiseLastMinute() {
        return connected ? noiseLastMinute : MobSenses.noise().lastMinute(System.currentTimeMillis());
    }

    public long alertedLastMinute() {
        return connected ? alertedLastMinute : MobSenses.alerted().lastMinute(System.currentTimeMillis());
    }

    public String worldsSummary() {
        return connected ? worldsSummary : MobSenses.settings().worldsSummary();
    }

    /** Current synced nearby mob alertness level ({@code 0=calm, 1=suspicious, 2=searching, 3=hunted}). */
    public int playerAlert() {
        return connected ? playerAlert : MobSenses.hud().mobAlertLevel();
    }

    /** Snapshot of the remote server's {@link ServerSettings}. */
    public ServerSettings remoteSettings() {
        return remoteSettings;
    }

    /**
     * Returns {@link #remoteSettings()} when connected to a remote Mob Senses server, or
     * {@link MobSenses#settings()} when in singleplayer / offline.
     */
    public ServerSettings effectiveSettings() {
        return connected ? remoteSettings : MobSenses.settings();
    }

    public boolean lastReplyOk() {
        return lastReplyOk;
    }

    public String lastReplyMessage() {
        return lastReplyMessage;
    }

    public long lastReplyTimestampMs() {
        return lastReplyTimestampMs;
    }

    public int adminReplyCount() {
        return adminReplyCount;
    }

    // -------------------------------------------------------------------------
    // Inbound S2C packet handlers
    // -------------------------------------------------------------------------

    /** Applies an incoming {@code mobsenses:state} payload. Returns {@code true} if valid. */
    public synchronized boolean onState(Properties p) {
        if (p == null) {
            return false;
        }
        int v = parseInt(p.getProperty("v", p.getProperty("protocol")), -1);
        if (v < 1) {
            return false;
        }

        ServerSettings synced = this.remoteSettings;
        synced.readState(p);

        this.currentWorld = clean(p.getProperty("world"));
        Preset parsedGlobal = Preset.byId(p.getProperty("server_preset"));
        Preset parsedEffective = Preset.byId(p.getProperty("preset"));
        this.serverPreset = parsedGlobal != null ? parsedGlobal : synced.preset();
        this.activePreset = parsedEffective != null
                ? parsedEffective
                : (!this.currentWorld.isEmpty() ? synced.presetOf(this.currentWorld) : this.serverPreset);
        this.allowClientHud = synced.allowClientHud();
        this.admin = parseBool(p.getProperty("admin"), false);
        this.debug = parseBool(p.getProperty("debug"), false);
        this.voiceInstalled = parseBool(p.getProperty("voice_installed"), false);
        this.noiseLastMinute = Math.max(0L, parseLong(p.getProperty("noise_min"), 0L));
        this.alertedLastMinute = Math.max(0L, parseLong(p.getProperty("alerted_min"), 0L));
        this.serverPlatform = clean(p.getProperty("platform"));
        this.worldsSummary = p.getProperty("worlds_summary") != null
                ? p.getProperty("worlds_summary").trim()
                : synced.worldsSummary();
        int alert = synced.allowClientHud()
                ? Math.max(StealthHudState.ALERT_CALM,
                        Math.min(StealthHudState.ALERT_HUNTED, parseInt(p.getProperty("player_alert"), 0)))
                : StealthHudState.ALERT_CALM;
        this.playerAlert = alert;
        MobSenses.hud().syncRemoteMobAlert(alert, System.currentTimeMillis());
        this.connected = true;
        return true;
    }

    public boolean onStateText(String raw) {
        return onState(LinkProtocol.readProperties(raw));
    }

    public boolean onStatePacket(byte[] packetBytes) {
        return onState(LinkProtocol.decodePacket(packetBytes));
    }

    /** Applies an incoming {@code mobsenses:admin_reply} payload. Returns {@code true} if valid. */
    public synchronized boolean onAdminReply(Properties p) {
        LinkProtocol.AdminReply parsed = LinkProtocol.parseAdminReply(p);
        if (parsed == null) {
            return false;
        }
        this.lastReplyOk = parsed.ok();
        this.lastReplyMessage = parsed.message();
        this.lastReplyTimestampMs = System.currentTimeMillis();
        this.adminReplyCount++;
        return true;
    }

    public boolean onAdminReplyText(String raw) {
        return onAdminReply(LinkProtocol.readProperties(raw));
    }

    public boolean onAdminReplyPacket(byte[] packetBytes) {
        return onAdminReply(LinkProtocol.decodePacket(packetBytes));
    }

    // -------------------------------------------------------------------------
    // Outbound C2S admin command sender hooks
    // -------------------------------------------------------------------------

    /** Registers how the client platform sends a {@code mobsenses:admin} {@link Properties} payload. */
    public void setAdminSender(Consumer<Properties> sender) {
        this.adminSender = sender;
    }

    /** Registers how the client platform sends a {@code mobsenses:admin} UTF string payload. */
    public void setAdminTextSender(final Consumer<String> textSender) {
        this.adminSender = textSender == null ? null : p -> textSender.accept(LinkProtocol.writeProperties(p));
    }

    /** Registers how the client platform sends a {@code mobsenses:admin} VarInt-framed byte packet. */
    public void setAdminPacketSender(final Consumer<byte[]> packetSender) {
        this.adminSender = packetSender == null ? null : p -> packetSender.accept(LinkProtocol.encodePacket(p));
    }

    public boolean sendAdmin(String command) {
        return sendAdmin(command, MobSenses.settings().language());
    }

    /** Sends an admin command over {@code mobsenses:admin} if a sender is registered. */
    public boolean sendAdmin(String command, String language) {
        Consumer<Properties> sender = this.adminSender;
        if (sender == null || command == null || Jv.isBlank(command)) {
            return false;
        }
        sender.accept(LinkProtocol.buildAdmin(command, language));
        return true;
    }

    /** Clears all remote server synchronization state when the client disconnects from a world/server. */
    public synchronized void resetClientState() {
        this.connected = false;
        this.allowClientHud = true;
        this.admin = false;
        this.debug = false;
        this.voiceInstalled = false;
        this.activePreset = Preset.DEFAULT;
        this.serverPreset = Preset.DEFAULT;
        this.currentWorld = "";
        this.serverPlatform = "";
        this.noiseLastMinute = 0L;
        this.alertedLastMinute = 0L;
        this.worldsSummary = "";
        this.playerAlert = 0;
        MobSenses.hud().syncRemoteMobAlert(0, 0L);
        this.lastReplyOk = true;
        this.lastReplyMessage = "";
        this.lastReplyTimestampMs = 0L;
        this.adminReplyCount = 0;
        this.remoteSettings = new ServerSettings(null);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || Jv.isBlank(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || Jv.isBlank(raw)) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || Jv.isBlank(raw)) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s) || "on".equals(s) || "yes".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "off".equals(s) || "no".equals(s)) {
            return false;
        }
        return fallback;
    }
}
