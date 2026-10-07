package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Set;

/** Which files the ffmpeg features take, by extension. */
public final class MediaFiles {
    private static final Set<String> AUDIO = Set.of(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "aif", "aiff", "ape", "wv", "ac3",
            "mka", "amr", "au", "caf", "w64", "mpc", "tta");
    private static final Set<String> VIDEO = Set.of(
            "mp4", "m4v", "mkv", "mov", "avi", "webm", "wmv", "flv", "mpg", "mpeg", "ts", "mts", "m2ts", "3gp",
            "vob", "ogv");
    private static final Set<String> TAGGABLE_AUDIO = Set.of("mp3", "m4a", "flac", "ogg", "oga", "opus");
    private static final Set<String> TAGGABLE_VIDEO = Set.of("mp4", "m4v", "mov", "3gp", "mkv", "webm");

    private MediaFiles() {
    }

    public static boolean areAllAudio(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, AUDIO::contains);
    }

    public static boolean areAllVideo(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, VIDEO::contains);
    }

    /** Each one audio or video, mixed allowed. */
    public static boolean areAllMedia(List<FileItem> items) {
        return FileItem.allFilesWithExtension(items, extension -> AUDIO.contains(extension) || VIDEO.contains(extension));
    }

    public static boolean isVideo(String extension) {
        return VIDEO.contains(extension);
    }

    /** Why Join Media can't join {@code items}, or null: it takes two or more audio or video files of one type. */
    public static String joinProblem(List<FileItem> items) {
        if (items == null || items.size() < 2 || !areAllMedia(items)) {
            return "Select two or more audio or video files.";
        }
        return items.stream().map(FileItem::extension).distinct().count() == 1 ? null
                : "Select files of one type only, such as all MP4 or all MP3.";
    }

    /** {@code name} ending in {@code .extension}: the joined file must keep the type of its parts. */
    public static String withExtension(String name, String extension) {
        String trimmed = name.trim();
        return FileItem.extension(trimmed).equals(extension) ? trimmed : trimmed + "." + extension;
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
