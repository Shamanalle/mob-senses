package com.kasper.mobsenses;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texts the server sends (command replies, messages to players), from the same language files as the
 * client: the {@code command.mobsenses.*} keys of {@code assets/mobsenses/lang/<language>.json}. A server
 * has no translation system of its own for mod texts, so the files are read here, once per language,
 * and each reply is written in the language of the player it goes to.
 */
public final class ServerText {

    public static final String PREFIX = "command.mobsenses.";
    public static final String DEFAULT_LANGUAGE = "en_us";
    /** The languages that have a file. */
    public static final String[] LANGUAGES = {"en_us", "ru_ru", "uk_ua", "de_de", "es_es", "pt_br", "zh_cn"};

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private ServerText() {
    }

    /**
     * The language file for a setting or a player's client language: "ru", "ru_ru", "ru-RU" and "RU" all
     * give "ru_ru", another country's variant of a known language its base language (pt_pt gives pt_br),
     * anything unknown English.
     */
    public static String language(String code) {
        if (Jv.isBlank(code)) {
            return DEFAULT_LANGUAGE;
        }
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (String l : LANGUAGES) {
            if (l.equals(c) || l.startsWith(c + "_")) {
                return l;
            }
        }
        int u = c.indexOf('_');
        String base = u > 0 ? c.substring(0, u) : c;
        for (String l : LANGUAGES) {
            if (l.startsWith(base + "_")) {
                return l;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    /** {@code key} (without the prefix) in {@code language}, with {@code %s} filled in; English when missing. */
    public static String get(String language, String key, Object... args) {
        String text = table(language(language)).get(PREFIX + key);
        if (text == null) {
            text = table(DEFAULT_LANGUAGE).get(PREFIX + key);
        }
        if (text == null) {
            ModLog.warn("No text for " + PREFIX + key);
            return key;
        }
        try {
            return args.length == 0 ? text : String.format(Locale.ROOT, text, args);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static Map<String, String> table(String language) {
        return CACHE.computeIfAbsent(language, ServerText::load);
    }

    private static Map<String, String> load(String language) {
        String path = "/assets/" + MobSenses.MOD_ID + "/lang/" + language + ".json";
        try (InputStream in = ServerText.class.getResourceAsStream(path)) {
            if (in == null) {
                ModLog.warn("Missing language file " + path);
                return Collections.emptyMap();
            }
            return parse(new String(Jv.readAll(in), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ModLog.failure("Reading " + path, e);
            return Collections.emptyMap();
        }
    }

    /** A flat {@code {"key": "text", ...}} object, as Minecraft's language files are. */
    static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        int[] pos = {json.indexOf('{') + 1};
        while (true) {
            skipSpace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) == '}') {
                return out;
            }
            String key = string(json, pos);
            skipSpace(json, pos);
            expect(json, pos, ':');
            skipSpace(json, pos);
            out.put(key, string(json, pos));
            skipSpace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') {
                pos[0]++;
            }
        }
    }

    private static void skipSpace(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static void expect(String s, int[] pos, char c) {
        if (pos[0] >= s.length() || s.charAt(pos[0]) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at " + pos[0]);
        }
        pos[0]++;
    }

    private static String string(String s, int[] pos) {
        expect(s, pos, '"');
        StringBuilder b = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = s.charAt(pos[0]++);
            switch (e) {
                case 'n':
                    b.append('\n');
                    break;
                case 't':
                    b.append('\t');
                    break;
                case 'r':
                    b.append('\r');
                    break;
                case 'b':
                    b.append('\b');
                    break;
                case 'f':
                    b.append('\f');
                    break;
                case 'u':
                    b.append((char) Integer.parseInt(s.substring(pos[0], pos[0] + 4), 16));
                    pos[0] += 4;
                    break;
                default:
                    b.append(e);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }
}
