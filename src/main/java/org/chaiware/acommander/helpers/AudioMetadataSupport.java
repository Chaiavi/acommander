package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** id3.exe: which files it edits, reading, writing and removing their tags. */
public final class AudioMetadataSupport {
    private static final Logger log = LoggerFactory.getLogger(AudioMetadataSupport.class);

    /** id3.exe {@code -q} format; {@link #QUERY_KEYS} are its fields in order. */
    private static final String QUERY_FORMAT = "%t\t%a\t%l\t%n\t%y\t%g\t%c";
    private static final List<String> QUERY_KEYS = List.of("title", "artist", "album", "track", "year", "genre", "comment");

    private AudioMetadataSupport() {
    }

    /** The tags of an mp3 by {@link #QUERY_KEYS}; all empty when id3.exe can't read them (e.g. the file has none). */
    public static Map<String, String> read(File mp3, Charset charset) throws IOException, InterruptedException {
        ProcessRunner.Result run = ProcessRunner.of(BundledTool.ID3.path().toString(), "-q", QUERY_FORMAT,
                mp3.getAbsolutePath()).charset(charset).run();
        if (!run.succeeded()) {
            log.warn("id3 read failed (exit {}): {}", run.exitCode(), run.stderrText());
            return parseQuery("");
        }
        return parseQuery(run.stdoutText());
    }

    /** {@code id3 <version flag> [-M] <changes> <file>}; {@code changes} is option, value, option, value, … */
    public static List<String> writeCommand(String versionFlag, boolean preserveTime, List<String> changes, File mp3) {
        List<String> command = new ArrayList<>(List.of(BundledTool.ID3.path().toString(), versionFlag));
        if (preserveTime) {
            command.add("-M");
        }
        command.addAll(changes);
        command.add(mp3.getAbsolutePath());
        return command;
    }

    /** Runs a {@link #writeCommand}; throws with id3.exe's output when it fails or can't take the text. */
    public static void write(List<String> command, Charset charset) throws IOException, InterruptedException {
        // the exe path is exempt: Windows starts the program from its Unicode path either way
        String unencodable = firstUnencodable(command.subList(1, command.size()), charset);
        if (unencodable != null) {
            throw new IOException("id3.exe can't take \"" + unencodable + "\": the Windows code page (" + charset.displayName()
                    + ") lacks some of its characters, and id3.exe has no UTF-8 input. Switch the Windows system locale "
                    + "to one that has them (for Hebrew: Windows-1255 or UTF-8), then restart the app.");
        }
        ProcessRunner.Result run = ProcessRunner.of(command).charset(charset).run();
        if (!run.succeeded()) {
            throw new IOException("id3.exe failed with exit code " + run.exitCode() + "\n\n"
                    + (run.stderrText() + "\n" + run.stdoutText()).trim());
        }
    }

    /** The first argument {@code charset} can't encode, or null. */
    static String firstUnencodable(List<String> command, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        return command.stream().filter(arg -> !encoder.canEncode(arg)).findFirst().orElse(null);
    }

    /** The Windows ANSI code page id3.exe reads and writes; it has no UTF-8 option. */
    public static Charset nativeCharset() {
        for (String name : new String[]{System.getProperty("native.encoding"), System.getProperty("sun.jnu.encoding")}) {
            try {
                if (name != null && !name.isBlank()) {
                    return Charset.forName(name);
                }
            } catch (IllegalArgumentException ignored) {
                // unknown or unsupported name: try the next one
            }
        }
        return Charset.defaultCharset();
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
