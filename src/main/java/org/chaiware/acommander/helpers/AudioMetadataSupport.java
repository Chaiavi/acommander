package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for checking if files are supported by id3.exe metadata editing.
 */
public final class AudioMetadataSupport {
    private static final Logger log = LoggerFactory.getLogger(AudioMetadataSupport.class);

    /** id3.exe {@code -q} format; {@link #QUERY_KEYS} are its fields in order. */
    public static final String QUERY_FORMAT = "%t\t%a\t%l\t%n\t%y\t%g\t%c";
    public static final List<String> QUERY_KEYS = List.of("title", "artist", "album", "track", "year", "genre", "comment");

    private AudioMetadataSupport() {
    }

    /** Values from {@code id3 -q QUERY_FORMAT}; every key is present, {@code <empty>} becomes "". */
    public static Map<String, String> parseQuery(String output) {
        String[] fields = output.replace("\r\n", "\n").replace("\r", "\n").trim().split("\t", -1);
        boolean matches = fields.length == QUERY_KEYS.size();
        if (!matches) {
            log.warn("Unexpected id3 query output: expected {} fields, got {}", QUERY_KEYS.size(), fields.length);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < QUERY_KEYS.size(); i++) {
            String value = matches ? fields[i].trim() : "";
            values.put(QUERY_KEYS.get(i), "<empty>".equalsIgnoreCase(value) ? "" : value);
        }
        return values;
    }

    /** Deletes the ID3v2 and the ID3v1 tag; true only if both deletes succeed. */
    public static boolean remove(File audio) throws IOException, InterruptedException {
        boolean v2 = deleteTag(audio, "-2");
        return deleteTag(audio, "-1") && v2;
    }

    private static boolean deleteTag(File audio, String tagVersionFlag) throws IOException, InterruptedException {
        return ProcessRunner.of(BundledTool.ID3.path().toString(), tagVersionFlag, "--delete", audio.getAbsolutePath())
                .mergeStderr().run().succeeded();
    }

    public static boolean areAllSupportedAudio(List<FileItem> selectedItems) {
        return FileItem.allFilesWithExtension(selectedItems, "mp3"::equals);
    }
}
