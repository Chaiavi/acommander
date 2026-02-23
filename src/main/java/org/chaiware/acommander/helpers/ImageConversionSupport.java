package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ImageConversionSupport {
    public static final List<String> OUTPUT_FORMATS = List.of("jpeg", "png", "gif", "webp", "tiff");
    private static final Set<String> CONVERTIBLE_INPUT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff"
    );

    private ImageConversionSupport() {
    }

    public static boolean areAllConvertibleImages(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(ImageConversionSupport::isConvertibleImage);
    }

    public static boolean isConvertibleImage(FileItem item) {
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
        return CONVERTIBLE_INPUT_EXTENSIONS.contains(extension);
    }

    public static List<String> targetFormatsForSelection(List<FileItem> selectedItems) {
        if (!areAllConvertibleImages(selectedItems)) {
            return List.of();
        }
        return OUTPUT_FORMATS;
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
