package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Set;

/** Which files the ffmpeg features take, by extension. */
public final class MediaFiles {
    private static final Set<String> AUDIO = Set.of(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "aif", "aiff", "ape", "wv", "ac3",
            "mka", "amr", "au", "caf", "w64", "mpc", "tta");
    private static final Set<String> TAGGABLE_AUDIO = Set.of("mp3", "m4a", "flac", "ogg", "oga", "opus");
    private static final Set<String> TAGGABLE_VIDEO = Set.of("mp4", "m4v", "mov", "3gp", "mkv", "webm");

    private MediaFiles() {
    }

    public static boolean areAllAudio(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, AUDIO::contains);
    }

    public static boolean areAllTaggableAudio(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, TAGGABLE_AUDIO::contains);
    }

    public static boolean areAllTaggableVideo(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, TAGGABLE_VIDEO::contains);
    }

    static boolean isTaggableAudio(String extension) {
        return TAGGABLE_AUDIO.contains(extension);
    }
}
