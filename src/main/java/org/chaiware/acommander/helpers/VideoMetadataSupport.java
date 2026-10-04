package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for checking if files are supported by AtomicParsley for metadata editing.
 */
public final class VideoMetadataSupport {
    private static final Logger log = LoggerFactory.getLogger(VideoMetadataSupport.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp4", "m4v", "3gp");

    private VideoMetadataSupport() {
    }

    /** Removes all metadata in place, keeping the file time. */
    public static boolean remove(File video) throws IOException, InterruptedException {
        Set<String> existingArtifacts = atomicParsleyArtifacts(video);
        try {
            return ProcessRunner.of(BundledTool.ATOMIC_PARSLEY.path().toString(), video.getAbsolutePath(),
                    "--metaEnema", "--preserveTime", "--overWrite").mergeStderr().run().succeeded();
        } finally {
            deleteNewArtifacts(video, existingArtifacts);
        }
    }

    /** AtomicParsley leaves {@code <name>-data-*} / {@code <name>-temp-*} files next to the video it edits. */
    public static Set<String> atomicParsleyArtifacts(File video) {
        Set<String> artifacts = new HashSet<>();
        File parent = video.getParentFile();
        File[] files = parent == null ? null : parent.listFiles();
        if (files == null) {
            return artifacts;
        }
        String name = video.getName();
        int dot = name.lastIndexOf('.');
        String baseName = dot <= 0 ? name : name.substring(0, dot);
        for (File file : files) {
            String fileName = file.getName();
            if (file.isFile() && (fileName.startsWith(baseName + "-data-") || fileName.startsWith(baseName + "-temp-"))) {
                artifacts.add(file.getAbsolutePath());
            }
        }
        return artifacts;
    }

    /** Deletes the artifacts that were not in {@code before}: the ones the last AtomicParsley run left. */
    public static void deleteNewArtifacts(File video, Set<String> before) {
        for (String path : atomicParsleyArtifacts(video)) {
            if (before.contains(path)) {
                continue;
            }
            if (new File(path).delete()) {
                log.info("Deleted AtomicParsley temp artifact: {}", path);
            } else {
                log.warn("Could not delete AtomicParsley temp artifact: {}", path);
            }
        }
    }

    public static boolean areAllSupportedVideos(List<FileItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        return selectedItems.stream().allMatch(VideoMetadataSupport::isSupportedVideo);
    }

    public static boolean isSupportedVideo(FileItem item) {
        if (item == null) {
            return false;
        }
        if ("..".equals(item.getPresentableFilename())) {
            return false;
        }
        if (item.isDirectory()) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(normalizedExtension(item));
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
