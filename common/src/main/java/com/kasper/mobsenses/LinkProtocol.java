package com.kasper.mobsenses;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Wire protocol and channel payloads shared by Fabric, NeoForge, Forge, and Bukkit/Paper/Folia.
 * <p>
 * Payloads are plain {@link Properties} text framed on the wire as a single Minecraft UTF-8 string
 * ({@code VarInt} byte length followed by UTF-8 bytes, capped at {@value #MAX_BYTES} bytes). Because
 * {@link #encodePacket(Properties)} and {@link #decodePacket(byte[])} use exact Minecraft string
 * framing, a Bukkit plugin channel ({@code Messenger}) and a modded client/server
 * ({@code FriendlyByteBuf.readUtf/writeUtf} or {@code ByteBufCodecs.stringUtf8}) interoperate
 * byte-for-byte without translation.
 */
public final class LinkProtocol {

    public static final int VERSION = 1;
    public static final String NAMESPACE = "mobsenses";

    public static final String HELLO_PATH = "hello";
    public static final String STATE_PATH = "state";
    public static final String ADMIN_PATH = "admin";
    public static final String ADMIN_REPLY_PATH = "admin_reply";

    public static final String HELLO_CHANNEL = NAMESPACE + ":" + HELLO_PATH;
    public static final String STATE_CHANNEL = NAMESPACE + ":" + STATE_PATH;
    public static final String ADMIN_CHANNEL = NAMESPACE + ":" + ADMIN_PATH;
    public static final String ADMIN_REPLY_CHANNEL = NAMESPACE + ":" + ADMIN_REPLY_PATH;

    /** Maximum payload size in bytes/chars on Minecraft custom payload channels. */
    public static final int MAX_BYTES = 32767;

    /** Parsed {@code mobsenses:hello} C2S handshake. */
    @com.github.bsideup.jabel.Desugar
    public record Hello(int version, String modVersion, String language) {
    }

    /** Parsed {@code mobsenses:admin} C2S command request. */
    @com.github.bsideup.jabel.Desugar
    public record AdminRequest(String command, String language) {
    }

    /** Parsed {@code mobsenses:admin_reply} S2C command response. */
    @com.github.bsideup.jabel.Desugar
    public record AdminReply(boolean ok, String message) {
    }

    private LinkProtocol() {
    }

    // -------------------------------------------------------------------------
    // HELLO (C2S: mobsenses:hello)
    // -------------------------------------------------------------------------

    public static Properties buildHello(String language) {
        return buildHello(BuildInfo.VERSION, language);
    }

    public static Properties buildHello(String modVersion, String language) {
        Properties p = new Properties();
        p.setProperty("v", String.valueOf(VERSION));
        p.setProperty("mod", Jv.isBlank(modVersion) ? "unknown" : modVersion.trim());
        p.setProperty("lang", Jv.isBlank(language) ? ServerText.DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT));
        return p;
    }

    public static String writeHello(String modVersion, String language) {
        return writeProperties(buildHello(modVersion, language));
    }

    public static byte[] encodeHello(String modVersion, String language) {
        return encodePacket(buildHello(modVersion, language));
    }

    public static Hello parseHello(Properties p) {
        if (p == null) {
            return null;
        }
        int v = parseInt(p.getProperty("v", p.getProperty("protocol")), -1);
        if (v < 1) {
            return null;
        }
        String mod = p.getProperty("mod", p.getProperty("mod_version", "unknown")).trim();
        String lang = p.getProperty("lang", ServerText.DEFAULT_LANGUAGE).trim().toLowerCase(Locale.ROOT);
        return new Hello(v, mod.isEmpty() ? "unknown" : mod, lang.isEmpty() ? ServerText.DEFAULT_LANGUAGE : lang);
    }

    public static Hello parseHello(String raw) {
        return parseHello(readProperties(raw));
    }

    public static Hello decodeHello(byte[] bytes) {
        return parseHello(decodePacket(bytes));
    }

    // -------------------------------------------------------------------------
    // STATE (S2C: mobsenses:state)
    // -------------------------------------------------------------------------

    /**
     * Builds a complete {@code mobsenses:state} payload for a player in {@code world}, reading current
     * server telemetry from {@link MobSenses}.
     */
    public static Properties buildState(ServerSettings settings, String world, boolean admin) {
        ServerSettings s = settings != null ? settings : MobSenses.settings();
        long now = System.currentTimeMillis();
        return buildState(
                s,
                world,
                admin,
                MobSenses.debug(),
                MobSenses.voiceChatInstalled(),
                MobSenses.noise().lastMinute(now),
                MobSenses.alerted().lastMinute(now),
                MobSenses.platform().toString());
    }

    /**
     * Builds a complete {@code mobsenses:state} payload with explicit runtime telemetry fields.
     */
    public static Properties buildState(ServerSettings settings, String world,
                                        boolean admin, boolean debug, boolean voiceInstalled,
                                        long noiseMin, long alertedMin, String platform) {
        Properties p = new Properties();
        p.setProperty("v", String.valueOf(VERSION));
        if (settings != null) {
            settings.writeState(p, world);
        }
        p.setProperty("admin", String.valueOf(admin));
        p.setProperty("debug", String.valueOf(debug));
        p.setProperty("voice_installed", String.valueOf(voiceInstalled));
        p.setProperty("noise_min", String.valueOf(Math.max(0L, noiseMin)));
        p.setProperty("alerted_min", String.valueOf(Math.max(0L, alertedMin)));
        if (platform != null && !Jv.isBlank(platform)) {
            p.setProperty("platform", platform.trim());
        }
        return p;
    }

    public static String writeState(ServerSettings settings, String world, boolean admin) {
        return writeProperties(buildState(settings, world, admin));
    }

    public static String writeState(ServerSettings settings, String world,
                                    boolean admin, boolean debug, boolean voiceInstalled,
                                    long noiseMin, long alertedMin, String platform) {
        return writeProperties(buildState(settings, world, admin, debug, voiceInstalled,
                noiseMin, alertedMin, platform));
    }

    public static byte[] encodeState(ServerSettings settings, String world, boolean admin) {
        return encodePacket(buildState(settings, world, admin));
    }

    public static byte[] encodeState(ServerSettings settings, String world,
                                     boolean admin, boolean debug, boolean voiceInstalled,
                                     long noiseMin, long alertedMin, String platform) {
        return encodePacket(buildState(settings, world, admin, debug, voiceInstalled,
                noiseMin, alertedMin, platform));
    }

    // -------------------------------------------------------------------------
    // ADMIN (C2S: mobsenses:admin) & ADMIN_REPLY (S2C: mobsenses:admin_reply)
    // -------------------------------------------------------------------------

    public static Properties buildAdmin(String command, String language) {
        Properties p = new Properties();
        p.setProperty("v", String.valueOf(VERSION));
        p.setProperty("cmd", command == null ? "" : command.trim());
        p.setProperty("lang", Jv.isBlank(language) ? ServerText.DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT));
        return p;
    }

    public static String writeAdmin(String command, String language) {
        return writeProperties(buildAdmin(command, language));
    }

    public static byte[] encodeAdmin(String command, String language) {
        return encodePacket(buildAdmin(command, language));
    }

    public static AdminRequest parseAdmin(Properties p) {
        if (p == null) {
            return null;
        }
        int v = parseInt(p.getProperty("v", p.getProperty("protocol")), -1);
        if (v < 1) {
            return null;
        }
        String cmd = p.getProperty("cmd", p.getProperty("command"));
        if (cmd == null) {
            return null;
        }
        String lang = p.getProperty("lang", "");
        return new AdminRequest(cmd.trim(), lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT));
    }

    public static AdminRequest parseAdmin(String raw) {
        return parseAdmin(readProperties(raw));
    }

    public static AdminRequest decodeAdmin(byte[] bytes) {
        return parseAdmin(decodePacket(bytes));
    }

    public static Properties buildAdminReply(boolean ok, String message) {
        Properties p = new Properties();
        p.setProperty("v", String.valueOf(VERSION));
        p.setProperty("ok", String.valueOf(ok));
        p.setProperty("msg", message == null ? "" : message);
        return p;
    }

    public static String writeAdminReply(boolean ok, String message) {
        return writeProperties(buildAdminReply(ok, message));
    }

    public static byte[] encodeAdminReply(boolean ok, String message) {
        return encodePacket(buildAdminReply(ok, message));
    }

    public static AdminReply parseAdminReply(Properties p) {
        if (p == null) {
            return null;
        }
        int v = parseInt(p.getProperty("v", p.getProperty("protocol")), -1);
        if (v < 1) {
            return null;
        }
        boolean ok = parseBool(p.getProperty("ok"), false);
        String msg = p.getProperty("msg", "");
        return new AdminReply(ok, msg == null ? "" : msg);
    }

    public static AdminReply parseAdminReply(String raw) {
        return parseAdminReply(readProperties(raw));
    }

    public static AdminReply decodeAdminReply(byte[] bytes) {
        return parseAdminReply(decodePacket(bytes));
    }

    /**
     * Executes an incoming {@code mobsenses:admin} C2S request against {@link AdminCommands} using the
     * sender's {@link AdminCommands.Context} and builds the {@code mobsenses:admin_reply} S2C payload.
     */
    public static Properties executeAdmin(Properties reqProps, final AdminCommands.Context baseCtx) {
        AdminRequest req = parseAdmin(reqProps);
        if (req == null || baseCtx == null) {
            return buildAdminReply(false, "Invalid admin request");
        }
        if (!baseCtx.hasPermission(AdminCommands.PERM_ADMIN)) {
            return buildAdminReply(false, "Permission denied");
        }
        final String effectiveLang = !Jv.isBlank(req.language()) ? req.language() : baseCtx.language();
        AdminCommands.Context ctx = new AdminCommands.Context() {
            @Override
            public String language() {
                return effectiveLang;
            }

            @Override
            public String world() {
                return baseCtx.world();
            }

            @Override
            public List<String> worlds() {
                return baseCtx.worlds();
            }

            @Override
            public boolean isDedicatedServer() {
                return baseCtx.isDedicatedServer();
            }

            @Override
            public boolean hasPermission(String node) {
                return baseCtx.hasPermission(node);
            }
        };
        String cmd = req.command();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.toLowerCase(Locale.ROOT).startsWith("mobsenses ")) {
            cmd = cmd.substring("mobsenses ".length()).trim();
        }
        Reply reply = AdminCommands.execute(cmd, ctx);
        return buildAdminReply(!reply.refused(), reply.summaryText());
    }

    // -------------------------------------------------------------------------
    // Properties & VarInt UTF-8 wire framing
    // -------------------------------------------------------------------------

    /** Serializes {@code p} to compact multi-line UTF text without timestamp comment headers. */
    public static String writeProperties(Properties p) {
        if (p == null) {
            return "";
        }
        StringWriter w = new StringWriter();
        try {
            p.store(w, null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        StringBuilder out = new StringBuilder();
        String normalized = w.toString().replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            if (!line.isEmpty() && !line.startsWith("#")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /** Parses {@link Properties} text, returning {@code null} when null, oversize, or malformed. */
    public static Properties readProperties(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_BYTES) {
            return null;
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return null;
        }
        Properties p = new Properties();
        try {
            p.load(new StringReader(raw));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return p;
    }

    /** Encodes {@code p} as a Minecraft VarInt-prefixed UTF-8 byte packet (max {@value #MAX_BYTES} bytes). */
    public static byte[] encodePacket(Properties p) {
        return encodeString(writeProperties(p));
    }

    /** Decodes a Minecraft VarInt-prefixed UTF-8 byte packet into {@link Properties}, or {@code null} if invalid. */
    public static Properties decodePacket(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES + 5) {
            return null;
        }
        String raw = decodeString(bytes);
        return raw == null ? null : readProperties(raw);
    }

    /** Encodes a string using Minecraft's standard VarInt UTF-8 length framing. */
    public static byte[] encodeString(String text) {
        String safe = text == null ? "" : text;
        byte[] utf8 = safe.getBytes(StandardCharsets.UTF_8);
        if (safe.length() > MAX_BYTES || utf8.length > MAX_BYTES) {
            throw new IllegalArgumentException("Packet payload exceeds MAX_BYTES (" + MAX_BYTES + "): " + utf8.length);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(utf8.length + 3);
        int value = utf8.length;
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        out.write(utf8, 0, utf8.length);
        return out.toByteArray();
    }

    /** Decodes a Minecraft VarInt-framed UTF-8 string, returning {@code null} if truncated, malformed, or oversize. */
    public static String decodeString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        int length = 0;
        int index = 0;
        for (int shift = 0; ; shift += 7) {
            if (index >= bytes.length || shift > 28) {
                return null;
            }
            byte b = bytes[index++];
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        if (length < 0 || length > MAX_BYTES || bytes.length - index < length) {
            return null;
        }
        String text = new String(bytes, index, length, StandardCharsets.UTF_8);
        return text.length() > MAX_BYTES ? null : text;
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
