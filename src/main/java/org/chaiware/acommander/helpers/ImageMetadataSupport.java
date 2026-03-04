package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for checking if files are supported by exiv2 for metadata editing.
 * Based on exiv2 supported formats: https://exiv2.org/manpage.html
 */
public final class ImageMetadataSupport {
    
    // Common image formats supported by exiv2
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            // JPEG family
            "jpg", "jpeg", "jpe", "jif", "jfif", "jfi",
            // TIFF family
            "tif", "tiff",
            // PNG
            "png",
            // WebP
            "webp",
            // GIF
            "gif",
            // BMP
            "bmp",
            // HEIF/HEIC
            "heic", "heif", "avif",
            // RAW formats (camera specific)
            "cr2", "cr3", "crw",  // Canon
            "nef",                // Nikon
            "orf",                // Olympus
            "raf",                // Fujifilm
            "arw",                // Sony
            "dng",                // Adobe DNG
            "rw2",                // Panasonic
            "pef",                // Pentax
            "sr2",                // Sigma
            "mrw",                // Minolta
            "x3f",                // Sigma
            "erf",                // Epson
            "3fr",                // Hasselblad
            "mef",                // Mamiya
            "mos",                // Leaf
            "iiq",                // Phase One
            "kdc",                // Kodak
            "dcr",                // Kodak
            "drf",                // Kodak
            "k25",                // Kodak
            // Other formats
            "jp2", "jpx",         // JPEG 2000
            "pgf",                // Progressive Graphics File
            "eps",                // Encapsulated PostScript
            "psd"                 // Photoshop
    );

    private ImageMetadataSupport() {
        // Private constructor to prevent instantiation
    }

    /**
     * Checks if all selected items are supported image files for metadata editing.
     */
    public static boolean areAllSupportedImages(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(ImageMetadataSupport::isSupportedImage);
    }

    /**
     * Checks if a single file item is a supported image format.
     */
    public static boolean isSupportedImage(FileItem item) {
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

    /**
     * Gets the normalized (lowercase) file extension without the dot.
     */
    public static String normalizedExtension(FileItem item) {
        String name = item == null ? "" : item.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Returns a human-readable list of supported formats.
     */
    public static String getSupportedFormatsDescription() {
        return "JPEG, TIFF, PNG, WebP, GIF, BMP, HEIC, AVIF, and various RAW formats (CR2, NEF, ARW, DNG, etc.)";
    }
}
