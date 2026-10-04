package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for checking if files are supported by UPX executable compression.
 */
public final class ExecutableCompressionSupport {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "exe",
            "dll",
            "ocx",
            "sys",
            "cpl",
            "scr"
    );

    /** What UPX does to the files; each is one UPX flag. */
    public enum UpxAction {
        GOOD("-9"), VERY_GOOD("--brute"), BEST("--ultra-brute"), DECOMPRESS("-d");

        public final String flag;

        UpxAction(String flag) {
            this.flag = flag;
        }
    }

    private ExecutableCompressionSupport() {
    }

    public static List<String> upxCommand(Path upxPath, UpxAction action, List<File> files) {
        List<String> command = new ArrayList<>(List.of(upxPath.toString(), action.flag));
        files.forEach(file -> command.add(file.getAbsolutePath()));
        return command;
    }

    /** "-37.50%" for a file that shrank from 800 to 500 bytes; "N/A" when the size before is unknown. */
    public static String percentChange(long sizeBefore, long sizeAfter) {
        if (sizeBefore <= 0) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.2f%%", ((double) sizeAfter - sizeBefore) / sizeBefore * 100.0);
    }

    public static boolean areAllSupportedExecutables(List<FileItem> selectedItems) {
        return FileItem.allFilesWithExtension(selectedItems, SUPPORTED_EXTENSIONS::contains);
    }
}
