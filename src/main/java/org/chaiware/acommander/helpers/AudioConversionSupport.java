package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Set;

public final class AudioConversionSupport {
    public static final List<String> OUTPUT_FORMATS = List.of(
            "wav", "flac", "ogg", "opus", "mp3", "aif", "caf", "au", "rf64", "w64", "raw", "aac", "m4a"
    );

    private static final Set<String> CONVERTIBLE_INPUT_EXTENSIONS = Set.of(
            "wav", "aif", "aiff", "au", "snd", "raw", "gsm", "vox", "paf", "fap", "svx",
            "nist", "sph", "voc", "ircam", "sf", "w64", "mat", "mat4", "mat5", "pvf", "xi",
            "htk", "sds", "avr", "wavex", "sd2", "flac", "caf", "wve", "prc", "oga", "ogg",
            "opus", "mpc", "rf64", "mp3", "aac", "m4a"
    );

    private AudioConversionSupport() {
    }

    public static boolean areAllConvertibleAudio(List<FileItem> selectedItems) {
        return FileItem.allFilesWithExtension(selectedItems, CONVERTIBLE_INPUT_EXTENSIONS::contains);
    }

    public static List<String> targetFormatsForSelection(List<FileItem> selectedItems) {
        if (!areAllConvertibleAudio(selectedItems)) {
            return List.of();
        }
        return OUTPUT_FORMATS;
    }
}
