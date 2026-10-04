package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Utility class for checking if files are supported by id3.exe metadata editing.
 */
public final class AudioMetadataSupport {

    private AudioMetadataSupport() {
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
