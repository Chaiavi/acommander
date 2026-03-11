package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

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

    private ExecutableCompressionSupport() {
    }

    public static boolean areAllSupportedExecutables(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(ExecutableCompressionSupport::isSupportedExecutable);
    }

    public static boolean isSupportedExecutable(FileItem item) {
        if (item == null) {
            return false;
        }
        if ("..".equals(item.getPresentableFilename())) {
            return false;
        }
        if (item.isDirectory()) {
            return false;
        }
        String extension = normalizedExtension(item);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    public static String normalizedExtension(FileItem item) {
        String name = item == null ? "" : item.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
