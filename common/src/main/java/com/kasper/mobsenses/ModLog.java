package com.kasper.mobsenses;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * The mod's log. Each platform hands in its own logger at startup (SLF4J on the mod loaders, the
 * plugin's logger on Bukkit); until then lines go to standard error.
 * <p>
 * A failure in the mod must never stop the game: callers catch it and report it here with
 * {@link #failure}, which logs each distinct problem once with its stack trace and then stays quiet.
 */
public final class ModLog {

    /** Where log lines go. */
    public interface Sink {
        void info(String message);

        void warn(String message, Throwable error);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void info(String message) {
            System.err.println("[Mob Senses] " + message);
        }

        @Override
        public void warn(String message, Throwable error) {
            System.err.println("[Mob Senses] " + message);
            if (error != null) {
                error.printStackTrace();
            }
        }
    };

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ModLog() {
    }

    public static void use(Sink s) {
        if (s != null) {
            sink = s;
        }
    }

    public static void info(String message) {
        sink.info(message);
    }

    public static void warn(String message) {
        sink.warn(message, null);
    }

    /**
     * Logs a caught failure of {@code what} once per kind of error, so a problem that repeats every
     * tick does not flood the log. The game goes on; the part that failed is skipped this time.
     */
    public static void failure(String what, Throwable error) {
        String key = what + ':' + error.getClass().getName() + ':' + error.getMessage();
        if (REPORTED.size() < 64 && REPORTED.add(key)) {
            sink.warn(what + " failed; the game goes on without it (reported once)", error);
        }
    }
}
