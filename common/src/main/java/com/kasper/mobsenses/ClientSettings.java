package com.kasper.mobsenses;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The client's HUD settings file, {@value #FILE_NAME}: controls whether the Stealth & Noise HUD is
 * shown, which corner or anchor of the screen it attaches to, its pixel offsets, opacity, compact
 * mode, and which bars ({@code Noise}, {@code Light}, {@code Scent}) are displayed.
 * <p>
 * Written with bilingual (English and Russian) comments on first launch and automatically reloaded
 * whenever the player edits and saves the file on disk — no restart and no command needed.
 */
public final class ClientSettings {

    public static final String FILE_NAME = "mobsenses-client.properties";

    /** Screen anchor for the Stealth & Noise HUD. */
    public enum Anchor {
        TOP_LEFT("top_left"),
        TOP_RIGHT("top_right"),
        BOTTOM_LEFT("bottom_left"),
        BOTTOM_RIGHT("bottom_right"),
        TOP_CENTER("top_center"),
        HOTBAR("hotbar");

        private final String id;

        Anchor(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Anchor byId(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            switch (s) {
                case "top_left":
                case "topleft":
                case "left_top":
                    return TOP_LEFT;
                case "top_right":
                case "topright":
                case "right_top":
                    return TOP_RIGHT;
                case "bottom_left":
                case "bottomleft":
                case "left_bottom":
                    return BOTTOM_LEFT;
                case "bottom_right":
                case "bottomright":
                case "right_bottom":
                    return BOTTOM_RIGHT;
                case "top_center":
                case "topcenter":
                case "top":
                case "center_top":
                    return TOP_CENTER;
                case "hotbar":
                case "above_hotbar":
                case "bottom_center":
                case "bottomcenter":
                case "bottom":
                    return HOTBAR;
                default:
                    return null;
            }
        }
    }

    private static final long RELOAD_POLL_INTERVAL_MS = 2_000L;

    private final Path file;
    private long modified = Long.MIN_VALUE;
    private long lastPollMs;

    private volatile boolean enabled = true;
    private volatile Anchor position = Anchor.TOP_LEFT;
    private volatile int x = 10;
    private volatile int y = 10;
    private volatile int opacity = 100;
    private volatile boolean compact = false;
    private volatile boolean autoHide = false;
    private volatile boolean showNoise = true;
    private volatile boolean showLight = true;
    private volatile boolean showScent = true;
    private volatile boolean showVoice = true;

    /** @param file the client settings file; {@code null} keeps everything in memory (tests) */
    public ClientSettings(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean enabled() {
        return enabled;
    }

    public Anchor position() {
        return position;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** Opacity in {@code 10..100} percent. */
    public int opacity() {
        return opacity;
    }

    /** Whether only the compact status header is shown without the meter bars. */
    public boolean compact() {
        return compact;
    }

    /** Whether the HUD hides itself when the player is completely quiet and hidden. */
    public boolean autoHide() {
        return autoHide;
    }

    public boolean showNoise() {
        return showNoise;
    }

    public boolean showLight() {
        return showLight;
    }

    public boolean showScent() {
        return showScent;
    }

    public boolean showVoice() {
        return showVoice;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPosition(Anchor position) {
        if (position != null) {
            this.position = position;
        }
    }

    public void setX(int x) {
        this.x = clampOffset(x);
    }

    public void setY(int y) {
        this.y = clampOffset(y);
    }

    public void setOpacity(int opacity) {
        this.opacity = clampOpacity(opacity);
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public void setAutoHide(boolean autoHide) {
        this.autoHide = autoHide;
    }

    public void setShowNoise(boolean showNoise) {
        this.showNoise = showNoise;
    }

    public void setShowLight(boolean showLight) {
        this.showLight = showLight;
    }

    public void setShowScent(boolean showScent) {
        this.showScent = showScent;
    }

    public void setShowVoice(boolean showVoice) {
        this.showVoice = showVoice;
    }

    /**
     * Computes the on-screen X coordinate of the HUD panel for the given screen and panel widths.
     */
    public int computeX(int screenWidth, int panelWidth) {
        int raw;
        switch (position) {
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
                raw = screenWidth - panelWidth - x;
                break;
            case TOP_CENTER:
            case HOTBAR:
                raw = (screenWidth - panelWidth) / 2 + x;
                break;
            case TOP_LEFT:
            case BOTTOM_LEFT:
            default:
                raw = x;
                break;
        }
        int maxX = Math.max(0, screenWidth - panelWidth);
        return Math.max(0, Math.min(maxX, raw));
    }

    /**
     * Computes the on-screen Y coordinate of the HUD panel for the given screen and panel heights.
     */
    public int computeY(int screenHeight, int panelHeight) {
        int raw;
        switch (position) {
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                raw = screenHeight - panelHeight - y;
                break;
            case HOTBAR:
                raw = screenHeight - 54 - panelHeight - y;
                break;
            case TOP_LEFT:
            case TOP_RIGHT:
            case TOP_CENTER:
            default:
                raw = y;
                break;
        }
        int maxY = Math.max(0, screenHeight - panelHeight);
        return Math.max(0, Math.min(maxY, raw));
    }

    /** Scales the alpha channel of an ARGB color by {@link #opacity()}. */
    public int applyOpacity(int argb) {
        int op = opacity;
        if (op >= 100) {
            return argb;
        }
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, (baseAlpha * op) / 100));
        return (argb & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    /**
     * Periodically checks if {@value #FILE_NAME} was modified on disk (at most once every 2 seconds)
     * and reloads it automatically.
     */
    public void pollReload(long nowMs) {
        if (file == null || nowMs - lastPollMs < RELOAD_POLL_INTERVAL_MS) {
            return;
        }
        lastPollMs = nowMs;
        if (reloadIfChanged()) {
            ModLog.info("Reloaded " + FILE_NAME + " (position=" + position.id() + ", x=" + x + ", y=" + y + ")");
        }
    }

    /** Reads the file, or writes it with defaults when it does not exist yet. */
    public synchronized void load() {
        if (file == null) {
            return;
        }
        try {
            if (!Files.exists(file)) {
                save();
                return;
            }
            modified = Files.getLastModifiedTime(file).toMillis();
            read(Jv.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Reading " + file, e);
        }
    }

    /** Reads the file again when it changed on disk since the last read or write. */
    public synchronized boolean reloadIfChanged() {
        if (file == null) {
            return false;
        }
        try {
            if (!Files.exists(file) || Files.getLastModifiedTime(file).toMillis() == modified) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        load();
        return true;
    }

    synchronized void read(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));

        String enabledRaw = p.getProperty("enabled", p.getProperty("hud", "true"));
        enabled = parseBool(enabledRaw, true);

        String posRaw = p.getProperty("position", p.getProperty("anchor", Anchor.TOP_LEFT.id()));
        Anchor parsedPos = Anchor.byId(posRaw);
        position = parsedPos != null ? parsedPos : Anchor.TOP_LEFT;

        x = clampOffset(parseInt(p, "x", 10));
        y = clampOffset(parseInt(p, "y", 10));
        opacity = clampOpacity(parseInt(p, "opacity", 100));
        compact = parseBool(p.getProperty("compact", "false"), false);
        autoHide = parseBool(p.getProperty("auto_hide", "false"), false);
        showNoise = parseBool(p.getProperty("show_noise", "true"), true);
        showLight = parseBool(p.getProperty("show_light", "true"), true);
        showScent = parseBool(p.getProperty("show_scent", "true"), true);
        showVoice = parseBool(p.getProperty("show_voice", "true"), true);
    }

    /** Writes the current client HUD settings with English and Russian comments. */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Jv.writeString(file, write(), StandardCharsets.UTF_8);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Writing " + file, e);
        }
    }

    synchronized String write() {
        List<String> lines = new ArrayList<>();
        lines.add("# Mob Senses: client HUD settings. Edit and save this file at any time; changes apply live in-game.");
        lines.add("# Mob Senses: настройки клиентского HUD. Изменения в этом файле применяются в игре сразу после сохранения.");
        lines.add("");
        lines.add("# Show the Stealth & Noise HUD on screen: true or false.");
        lines.add("# Показывать ли HUD скрытности и шума на экране: true или false.");
        lines.add("enabled=" + enabled);
        lines.add("");
        lines.add("# Where to place the HUD: top_left, top_right, bottom_left, bottom_right, top_center, or hotbar.");
        lines.add("# Где располагать HUD: top_left, top_right, bottom_left, bottom_right, top_center или hotbar.");
        lines.add("position=" + position.id());
        lines.add("");
        lines.add("# Horizontal offset in pixels from the chosen position (-2000 to 2000).");
        lines.add("# Горизонтальный отступ в пикселях от выбранной позиции (-2000 до 2000).");
        lines.add("x=" + x);
        lines.add("");
        lines.add("# Vertical offset in pixels from the chosen position (-2000 to 2000).");
        lines.add("# Вертикальный отступ в пикселях от выбранной позиции (-2000 до 2000).");
        lines.add("y=" + y);
        lines.add("");
        lines.add("# HUD opacity in percent (10 to 100, where 100 is fully opaque).");
        lines.add("# Непрозрачность HUD в процентах (от 10 до 100, где 100 — стандартная).");
        lines.add("opacity=" + opacity);
        lines.add("");
        lines.add("# Compact mode (true = only the status line without bars, false = full panel with bars).");
        lines.add("# Компактный режим (true = только строка статуса без полосок, false = полная панель с полосками).");
        lines.add("compact=" + compact);
        lines.add("");
        lines.add("# Automatically hide the HUD when completely quiet, in the dark, not bleeding and not speaking.");
        lines.add("# Автоматически скрывать HUD, когда вы стоите тихо, не светитесь, не истекаете кровью и молчите.");
        lines.add("auto_hide=" + autoHide);
        lines.add("");
        lines.add("# Which bars and indicators to show: true or false.");
        lines.add("# Какие шкалы и индикаторы показывать: true или false.");
        lines.add("show_noise=" + showNoise);
        lines.add("show_light=" + showLight);
        lines.add("show_scent=" + showScent);
        lines.add("show_voice=" + showVoice);
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "on".equals(s) || "yes".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "off".equals(s) || "no".equals(s) || "0".equals(s)) {
            return false;
        }
        return fallback;
    }

    private static int parseInt(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampOffset(int value) {
        return Math.max(-2000, Math.min(2000, value));
    }

    private static int clampOpacity(int value) {
        return Math.max(10, Math.min(100, value));
    }
}
