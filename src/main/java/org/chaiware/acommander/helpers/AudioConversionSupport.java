package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AudioConversionSupport {
    public static final List<String> OUTPUT_FORMATS = List.of(
            "wav", "flac", "ogg", "opus", "mp3", "aif", "caf", "au", "rf64", "w64", "raw"
    );

    private static final Set<String> CONVERTIBLE_INPUT_EXTENSIONS = Set.of(
            "wav", "aif", "aiff", "au", "snd", "raw", "gsm", "vox", "paf", "fap", "svx",
            "nist", "sph", "voc", "ircam", "sf", "w64", "mat", "mat4", "mat5", "pvf", "xi",
            "htk", "sds", "avr", "wavex", "sd2", "flac", "caf", "wve", "prc", "oga", "ogg",
            "opus", "mpc", "rf64", "mp3"
    );

    private AudioConversionSupport() {
    }

    public static boolean areAllConvertibleAudio(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(AudioConversionSupport::isConvertibleAudio);
    }

    public static boolean isConvertibleAudio(FileItem item) {
        if (item == null || item.isDirectory()) {
            return false;
        }
        if ("..".equals(item.getPresentableFilename())) {
            return false;
        }
        return CONVERTIBLE_INPUT_EXTENSIONS.contains(normalizedExtension(item));
    }

    public static List<String> targetFormatsForSelection(List<FileItem> selectedItems) {
        if (!areAllConvertibleAudio(selectedItems)) {
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
