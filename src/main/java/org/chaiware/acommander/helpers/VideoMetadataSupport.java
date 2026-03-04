package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for checking if files are supported by AtomicParsley for metadata editing.
 */
public final class VideoMetadataSupport {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "m4v", "3gp");

    private VideoMetadataSupport() {
    }

    public static boolean areAllSupportedVideos(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(VideoMetadataSupport::isSupportedVideo);
    }

    public static boolean isSupportedVideo(FileItem item) {
        if (item == null) {
            return false;
        }
        if ("..".equals(item.getPresentableFilename())) {
            return false;
        }
        if (item.isDirectory()) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(normalizedExtension(item));
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
