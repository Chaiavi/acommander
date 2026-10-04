package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.FileItem;

import java.util.Locale;
import java.util.Set;

/** The glyph and colour shown before each name in the panes. */
public final class FileIcons {

    public record Icon(String glyph, String textColor) {}

    private static final Set<String> TEXT = Set.of("txt", "md", "log", "json", "xml", "yml", "yaml", "csv", "ini",
            "conf", "properties", "gradle", "kts", "java", "kt", "js", "ts", "html", "css");
    private static final Set<String> IMAGE = Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico");
    private static final Set<String> AUDIO = Set.of("mp3", "wav", "flac", "aac", "ogg", "opus", "m4a");
    private static final Set<String> VIDEO = Set.of("mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v");
    private static final Set<String> EXECUTABLE = Set.of("exe", "msi", "bat", "cmd", "ps1", "sh");

    private FileIcons() {
    }

    public static boolean isExecutableExtension(String extension) {
        return EXECUTABLE.contains(extension);
    }

    public static Icon of(FileItem item) {
        if ("..".equals(item.getPresentableFilename())) {
            return new Icon("↩", "#E0E0E0");
        }
        if (item.isDirectory()) {
            return new Icon("📁", "#FFD54F");
        }

        String name = item.getName();
        int lastDot = name.lastIndexOf('.');
        String extension = lastDot >= 0 && lastDot < name.length() - 1
                ? name.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";

        if (ArchiveMode.isReadWriteExtension(extension) || ArchiveMode.isReadOnlyExtension(extension)) {
            return new Icon("📦", "#FFB74D");
        }
        if ("pdf".equals(extension)) {
            return new Icon("📕", "#EF9A9A");
        }
        if (TEXT.contains(extension)) {
            return new Icon("📄", "#C8E6C9");
        }
        if (IMAGE.contains(extension)) {
            return new Icon("🖼", "#B2EBF2");
        }
        if (AUDIO.contains(extension)) {
            return new Icon("🎵", "#FFE0B2");
        }
        if (VIDEO.contains(extension)) {
            return new Icon("🎬", "#F8BBD0");
        }
        if (EXECUTABLE.contains(extension)) {
            return new Icon("⚙", "#CFD8DC");
        }
        return new Icon("📃", "#E0E0E0");
    }
}
