package com.kasper.mobsenses;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The few library calls newer than Java 8 that the shared code would use (collection factories, String
 * and Files helpers). The plugin and, later, the Minecraft 1.16.5 builds run on Java 8, so the code goes
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
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String readString(Path file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file), charset);
    }

    public static void writeString(Path file, String text, Charset charset) throws IOException {
        Files.write(file, text.getBytes(charset));
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
