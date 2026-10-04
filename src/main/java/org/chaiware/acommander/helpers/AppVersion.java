package org.chaiware.acommander.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The running app's version, written into {@code app-version.properties} by the build from {@code appVersion}. */
public final class AppVersion {
    private static final String CURRENT = load();

    private AppVersion() {
    }

    public static String current() {
        return CURRENT;
    }

    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream("/app-version.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            // An unexpanded "${appVersion}" means the resource was copied without the Gradle build.
            return version.isEmpty() || version.contains("$") ? "dev" : version;
        } catch (IOException e) {
            return "dev";
        }
    }
}
