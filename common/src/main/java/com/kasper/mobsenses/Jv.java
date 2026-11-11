package com.kasper.mobsenses;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The few library calls newer than Java 8 that the shared code would use (collection factories, String
 * and Files helpers). The plugin and the Minecraft 1.16.5 builds run on Java 8, so the code goes
 * through these instead.
 */
public final class Jv {

    private Jv() {
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    public static <T> List<T> copyOf(Collection<? extends T> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    public static <T> Set<T> copyOfSet(Collection<? extends T> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(items));
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String strip(String s) {
        return s == null ? "" : s.trim();
    }

    public static String readString(Path file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file), charset);
    }

    public static void writeString(Path file, String text, Charset charset, OpenOption... options) throws IOException {
        Files.write(file, text.getBytes(charset), options);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * The parameter types of a record class's canonical constructor, or {@code null} when it is not a
     * record (or before Java 16).
     */
    public static Class<?>[] recordTypes(Class<?> type) {
        try {
            if (!(Boolean) Class.class.getMethod("isRecord").invoke(type)) {
                return null;
            }
            Object[] parts = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            Class<?>[] types = new Class<?>[parts.length];
            for (int i = 0; i < parts.length; i++) {
                types[i] = (Class<?>) parts[i].getClass().getMethod("getType").invoke(parts[i]);
            }
            return types;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
