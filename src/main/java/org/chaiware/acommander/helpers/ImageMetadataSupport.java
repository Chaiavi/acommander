package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    /** One {@code exiv2 -pa} line; {@code value} as shown to the user (see {@link #displayValue}). */
    public record Exiv2Entry(String key, String type, String value) {}

    /** Parses {@code exiv2 -pa}, one "key type count value" per line; lines without a value are skipped. */
    public static List<Exiv2Entry> parsePrintAll(String output) {
        List<Exiv2Entry> entries = new ArrayList<>();
        if (output == null) {
            return entries;
        }
        for (String line : output.split("\\R")) {
            String[] parts = line.trim().split("\\s+", 4);
            if (parts.length == 4 && !parts[0].startsWith("ERROR")) {
                entries.add(new Exiv2Entry(parts[0], parts[1], displayValue(parts[1], parts[3].trim())));
            }
        }
        return entries;
    }

    /** A LangAlt value without its {@code lang="x-default"} prefix; any other value unchanged. */
    public static String displayValue(String type, String value) {
        return "LangAlt".equalsIgnoreCase(type) ? value.replaceFirst("^lang=\"[^\"]+\"\\s+", "") : value;
    }

    /** Tree group of a key: "EXIF - Image" for {@code Exif.Image.Make}, likewise IPTC / XMP; else Comment, Thumbnail, Other. */
    public static String groupName(String key) {
        String family = key.startsWith("Exif.") ? "EXIF" : key.startsWith("Iptc.") ? "IPTC" : key.startsWith("Xmp.") ? "XMP" : null;
        if (family != null) {
            String[] parts = key.split("\\.");
            return parts.length >= 3 ? family + " - " + parts[1] : family;
        }
        if (key.startsWith("Comment")) {
            return "Comment";
        }
        return key.startsWith("Thumbnail") ? "Thumbnail" : "Other";
    }

    /** Deletes all metadata (EXIF, IPTC, XMP, comment) in place. */
    public static boolean remove(File image) throws IOException, InterruptedException {
        return ProcessRunner.of(BundledTool.EXIV2.path().toString(), "-d", "a", image.getAbsolutePath())
                .mergeStderr().run().succeeded();
    }

    /**
     * Checks if all selected items are supported image files for metadata editing.
     */
    public static boolean areAllSupportedImages(List<FileItem> selectedItems) {
        return FileItem.allFilesWithExtension(selectedItems, SUPPORTED_EXTENSIONS::contains);
    }
}
