package com.kasper.mobsenses;

import java.io.InputStream;
import java.util.Properties;

/** The mod's version, written into {@code mobsenses-build.properties} by the build. */
public final class BuildInfo {

    private static final String VERSION = read();

    private BuildInfo() {
    }

    public static String version() {
        return VERSION;
    }

    private static String read() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/mobsenses-build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("version", "dev");
            }
        } catch (Exception ignored) {
            // A jar without the file is a development build
        }
        return "dev";
    }
}
