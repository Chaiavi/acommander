package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Set;

public final class ImageConversionSupport {
    public static final List<String> OUTPUT_FORMATS = List.of("jpeg", "png", "gif", "webp", "tiff");
    private static final Set<String> CONVERTIBLE_INPUT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff"
    );

    private ImageConversionSupport() {
    }

    public static boolean areAllConvertibleImages(List<FileItem> selectedItems) {
        return FileItem.allFilesWithExtension(selectedItems, CONVERTIBLE_INPUT_EXTENSIONS::contains);
    }

    public static List<String> targetFormatsForSelection(List<FileItem> selectedItems) {
        if (!areAllConvertibleImages(selectedItems)) {
            return List.of();
        }
        return OUTPUT_FORMATS;
    }
}
