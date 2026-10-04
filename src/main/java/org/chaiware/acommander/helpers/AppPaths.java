package org.chaiware.acommander.helpers;

import java.nio.file.Path;

/** Where the app's own files live: the app root is the working dir ({@code user.dir}), with config/ and apps/ under it. */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path root() {
        return Path.of(System.getProperty("user.dir"));
    }

    public static Path config(String fileName) {
        return root().resolve("config").resolve(fileName);
    }

    /** A path relative to the app root (as written in apps.json), or the path itself when it is absolute. */
    public static Path resolve(String path) {
        return root().resolve(path);
    }
}
