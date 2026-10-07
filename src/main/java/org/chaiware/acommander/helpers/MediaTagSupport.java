package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads, writes and strips the tags of audio and video files with ffmpeg. */
public final class MediaTagSupport {
    private MediaTagSupport() {
    }

    /** The file's global tags, keys lower-case (ffmpeg names: title, artist, date, track, …). */
    public static Map<String, String> read(File file) throws IOException, InterruptedException {
        ProcessRunner.Result run = ProcessRunner.of(BundledTool.FFMPEG.path().toString(), "-hide_banner", "-nostdin",
                "-loglevel", "error", "-i", file.getAbsolutePath(), "-f", "ffmetadata", "-").run();
        if (!run.succeeded()) {
            throw new IOException("ffmpeg failed with exit code " + run.exitCode() + "\n\n" + run.stderrText().trim());
        }
        return parseFfmetadata(run.stdoutText());
    }

    /** Sets each tag in {@code changes} (an empty value deletes it); copies the streams, so nothing is re-encoded. */
    public static void write(File file, Map<String, String> changes, boolean preserveTime) throws IOException, InterruptedException {
        List<String> options = new ArrayList<>(List.of("-map", "0", "-c", "copy", "-map_metadata", "0", "-map_chapters", "0"));
        changes.forEach((key, value) -> {
            options.add("-metadata");
            options.add(key + "=" + value);
        });
        rewrite(file, options, preserveTime);
    }

    /** Drops every tag and chapter, and the cover art of an audio file; keeps the file time. */
    public static boolean remove(File file) throws IOException, InterruptedException {
        String streams = MediaFiles.isTaggableAudio(FileItem.extension(file.getName())) ? "0:a" : "0";
        rewrite(file, List.of("-map", streams, "-c", "copy", "-map_metadata", "-1", "-map_chapters", "-1"), true);
        return true;
    }

    /** ffmpeg writes a copy next to the file; only a finished copy replaces it. */
    private static void rewrite(File file, List<String> options, boolean preserveTime) throws IOException, InterruptedException {
        Path source = file.toPath();
        Path temp = source.resolveSibling(tempName(file.getName()));
        FileTime modified = Files.getLastModifiedTime(source);
        try {
            ProcessRunner.Result run = ProcessRunner.of(rewriteCommand(BundledTool.FFMPEG.path(), source, temp, options)).run();
            if (!run.succeeded()) {
                throw new IOException("ffmpeg failed with exit code " + run.exitCode() + "\n\n" + run.stderrText().trim());
            }
            Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (preserveTime) {
                Files.setLastModifiedTime(source, modified);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static List<String> rewriteCommand(Path ffmpeg, Path source, Path temp, List<String> options) {
        List<String> command = new ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-loglevel", "error",
                "-y", "-i", source.toString()));
        command.addAll(options);
        // bitexact: no "encoder=Lavf…" tag of our own
        command.addAll(List.of("-fflags", "+bitexact"));
        if ("mp3".equals(FileItem.extension(source.getFileName().toString()))) {
            // ID3v2.3: the version Windows Explorer reads
            command.addAll(List.of("-id3v2_version", "3"));
        }
        command.add(temp.toString());
        return command;
    }

    /** Keeps the extension, which tells ffmpeg the container to write. */
    static String tempName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name + ".acommander-tags" : name.substring(0, dot) + ".acommander-tags" + name.substring(dot);
    }

    /** The global section of ffmpeg's ffmetadata output; {@code \} escapes {@code = ; # \} and newlines. */
    public static Map<String, String> parseFfmetadata(String text) {
        Map<String, String> tags = new LinkedHashMap<>();
        StringBuilder line = new StringBuilder();
        int equals = -1;
        boolean literalStart = false;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '\n';
            if (c == '\\' && i + 1 < text.length()) {
                literalStart |= line.isEmpty();
                line.append(text.charAt(++i));
                continue;
            }
            if (c == '\r') {
                continue;
            }
            if (c != '\n') {
                if (c == '=' && equals < 0) {
                    equals = line.length();
                }
                line.append(c);
                continue;
            }
            char first = line.isEmpty() || literalStart ? 0 : line.charAt(0);
            if (first == '[') {
                break;
            }
            if (first != ';' && first != '#' && equals > 0) {
                tags.putIfAbsent(line.substring(0, equals).toLowerCase(Locale.ROOT), line.substring(equals + 1));
            }
            line.setLength(0);
            equals = -1;
            literalStart = false;
        }
        return tags;
    }
}
