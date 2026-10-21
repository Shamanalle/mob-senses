package com.kasper.mobsenses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against untranslated keys reaching players: every key the code uses must exist in every
 * language file, and all language files must have the same keys and placeholders.
 */
public class LangConsistencyTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path LANG = ROOT.resolve("common/src/main/resources/assets/mobsenses/lang");

    private static Map<String, String> english;
    /** Every other language, by file name. */
    private static final Map<String, Map<String, String>> others = new TreeMap<>();
    private static String sources;

    @BeforeAll
    static void load() throws IOException {
        english = readLang(LANG.resolve("en_us.json"));
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".json") && !f.endsWith("en_us.json")).toList()) {
                others.put(f.getFileName().toString(), readLang(f));
            }
        }
        StringBuilder all = new StringBuilder();
        for (String dir : new String[]{"common/src/main", "shared", "versions", "bukkit/src"}) {
            Path p = ROOT.resolve(dir);
            assertTrue(Files.isDirectory(p), "missing " + p);
            try (Stream<Path> files = Files.walk(p)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".java") && !generated(p.relativize(f))).toList()) {
                    all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        sources = all.toString();
    }

    /** Under a module's build folder (the Minecraft sources a loader unpacks, for one). */
    private static boolean generated(Path relative) {
        for (Path part : relative) {
            if (part.toString().equals("build") || part.toString().equals(".gradle")) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Every language of ServerText has a file")
    void everyLanguageHasAFile() {
        for (String l : ServerText.LANGUAGES) {
            assertTrue(Files.isRegularFile(LANG.resolve(l + ".json")), l);
        }
        assertEquals(ServerText.LANGUAGES.length, others.size() + 1, "a language file that ServerText does not list");
    }

    @Test
    @DisplayName("All language files contain the same keys")
    void sameKeys() {
        assertTrue(others.containsKey("ru_ru.json"), "Russian is required");
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(lang.getValue().keySet()), lang.getKey());
        }
    }

    @Test
    @DisplayName("Placeholders match between languages")
    void samePlaceholders() {
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.get(key)), placeholders(lang.getValue().get(key)),
                        "Placeholders differ for " + key + " in " + lang.getKey());
            }
        }
    }

    @Test
    @DisplayName("No text is empty")
    void noEmptyText() {
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            lang.getValue().forEach((k, v) -> assertFalse(v.isBlank(), k + " in " + lang.getKey()));
        }
        english.forEach((k, v) -> assertFalse(v.isBlank(), k));
    }

    @Test
    @DisplayName("Every key used by the code is translated")
    void usedKeysExist() {
        Set<String> used = new TreeSet<>();
        Matcher full = Pattern.compile("\"((?:command|gui|key|message)\\.mobsenses\\.[\\w.\\-]*)\"").matcher(sources);
        while (full.find()) {
            if (!full.group(1).endsWith(".")) {
                used.add(full.group(1));
            }
        }
        // ServerText.get(lang, "status.title", ...): keys relative to command.mobsenses.
        Matcher relative = Pattern.compile("ServerText\\.get\\([^,()]+,\\s*\"([\\w.]+)\"").matcher(sources);
        while (relative.find()) {
            if (!relative.group(1).endsWith(".")) {
                used.add(ServerText.PREFIX + relative.group(1));
            }
        }
        for (Preset p : Preset.values()) {
            used.add(ServerText.PREFIX + p.nameKey());
        }
        for (String sense : new String[]{"hearing", "sight"}) {
            used.add(ServerText.PREFIX + "sense." + sense);
        }
        for (String sub : new String[]{"status", "preset", "hearing", "sight", "voice", "world", "reload", "help"}) {
            used.add(ServerText.PREFIX + "help." + sub);
        }
        List<String> missing = new ArrayList<>();
        for (String k : used) {
            if (!english.containsKey(k)) {
                missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }

    private static int placeholders(String s) {
        Matcher m = Pattern.compile("%(?:\\d+\\$)?s").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Reads a language file with the same parser the server uses, and fails on a repeated key. */
    private static Map<String, String> readLang(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Matcher keys = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:").matcher(json);
        Set<String> seen = new TreeSet<>();
        while (keys.find()) {
            assertTrue(seen.add(keys.group(1)), "Duplicate key " + keys.group(1) + " in " + file.getFileName());
        }
        Map<String, String> map = new TreeMap<>(ServerText.parse(json));
        assertFalse(map.isEmpty(), "No entries in " + file);
        return map;
    }
}
