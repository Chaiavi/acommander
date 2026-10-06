package org.chaiware.acommander.services;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Items of a copy or move whose name already exists in the target folder, and what to do with them. */
public final class TransferConflicts {

    /** One choice for the whole batch; each maps to the FastCopy mode that applies it. */
    public enum Policy {
        OVERWRITE("force_copy"),
        SKIP("noexist_only"),
        OVERWRITE_OLDER("update");

        private final String fastCopyMode;

        Policy(String fastCopyMode) {
            this.fastCopyMode = fastCopyMode;
        }

        public String fastCopyMode() {
            return fastCopyMode;
        }
    }

    /** {@code source} is null when the source folder listing didn't have it. */
    public record Conflict(String name, FileItem source, FileItem target) {
        public boolean directory() {
            return target.isDirectory();
        }

        public boolean sourceIsNewer() {
            return source != null && !source.isDirectory() && !target.isDirectory()
                    && source.modifiedMillis() > target.modifiedMillis();
        }

        public boolean targetIsNewer() {
            return source != null && !source.isDirectory() && !target.isDirectory()
                    && target.modifiedMillis() > source.modifiedMillis();
        }

        /** "1.5 MB, 06/10/2026 12:00, Newer"; "Folder" for a folder. */
        public static String describe(FileItem item, boolean newer) {
            if (item == null) {
                return "";
            }
            String text = item.isDirectory() ? "Folder" : FileItem.humanSize(item.getSizeInBytes()) + ", " + item.getDate();
            return newer ? text + ", Newer" : text;
        }
    }

    private TransferConflicts() {
    }

    /** Lists the target folder (and the source one only when something clashes). Blocking; FTP is a round trip. */
    public static List<Conflict> find(ClipboardTransfer.State source, VFileSystem targetFs, String targetFolder) throws IOException {
        Function<String, String> key = keyFor(targetFs);
        Map<String, FileItem> existing = byName(targetFs.listContents(targetFolder), key);
        List<Entry> clashing = source.entries().stream().filter(entry -> existing.containsKey(key.apply(entry.name()))).toList();
        if (clashing.isEmpty()) {
            return List.of();
        }
        Function<String, String> sourceKey = keyFor(source.sourceFs());
        Map<String, FileItem> sourceItems = byName(source.sourceFs().listContents(source.sourceFolder()), sourceKey);
        return clashing.stream()
                .map(entry -> new Conflict(entry.name(), sourceItems.get(sourceKey.apply(entry.name())),
                        existing.get(key.apply(entry.name()))))
                .toList();
    }

    /**
     * The entries to transfer under {@code policy}: SKIP drops every conflict, OVERWRITE_OLDER keeps only files whose
     * source is newer. ponytail: whole items only; a clashing folder can't be merged file by file here, so OLDER drops
     * it. FastCopy's own modes handle the files inside folders for a local copy, which doesn't call this.
     */
    public static List<Entry> keep(List<Entry> entries, List<Conflict> conflicts, Policy policy) {
        if (policy == Policy.OVERWRITE || conflicts.isEmpty()) {
            return entries;
        }
        Map<String, Conflict> byName = conflicts.stream().collect(Collectors.toMap(Conflict::name, c -> c, (a, b) -> a));
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            Conflict conflict = byName.get(entry.name());
            if (conflict == null || (policy == Policy.OVERWRITE_OLDER && conflict.sourceIsNewer())) {
                kept.add(entry);
            }
        }
        return kept;
    }

    private static Function<String, String> keyFor(VFileSystem fs) {
        return fs instanceof FtpFileSystem ? Objects::requireNonNull : name -> name.toLowerCase(Locale.ROOT);
    }

    private static Map<String, FileItem> byName(List<FileItem> items, Function<String, String> key) {
        return items.stream()
                .filter(item -> !"..".equals(item.getPresentableFilename()))
                .collect(Collectors.toMap(item -> key.apply(item.getName()), item -> item, (a, b) -> a));
    }
}
