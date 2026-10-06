package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.Operation;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Copy / cut / paste of pane items between folders on any file system (local, archive, FTP). */
public final class ClipboardTransfer {
    private static final Logger log = LoggerFactory.getLogger(ClipboardTransfer.class);

    public record Entry(String name, boolean directory, String sourceInternalPath) {}

    /** What was copied or cut, and where from. */
    public record State(List<Entry> entries, boolean cut, FilesPanesHelper.FocusSide sourceSide, VFileSystem sourceFs,
                        String sourceFolder) {}

    /** {@code pasted} carries the name each item got in the target folder; {@code firstFailure} is null when none failed. */
    public record PasteResult(List<Entry> pasted, List<Entry> failed, Exception firstFailure) {}

    private ClipboardTransfer() {
    }

    /**
     * The selected items with their full paths on {@code fs}, read now: an FTP item is only a name, resolved against
     * the pane's folder, which may change before the operation runs. ".." is dropped. Call on the FX thread.
     */
    public static State capture(List<FileItem> items, boolean cut, FilesPanesHelper.FocusSide side, VFileSystem fs,
                                String folder) {
        List<Entry> entries = items.stream()
                .filter(item -> !"..".equals(item.getPresentableFilename()))
                .map(item -> new Entry(item.getName(), item.isDirectory(), fs.getInternalPath(item)))
                .toList();
        return new State(entries, cut, side, fs, folder);
    }

    /**
     * Pastes every entry into {@code targetFolder} (a cut moves). A copy into its own source folder gets a
     * {@link #duplicateName}. Blocking; a failed item is logged and listed, the rest still run.
     */
    public static PasteResult paste(State state, VFileSystem targetFs, String targetFolder) {
        boolean duplicate = !state.cut() && isSameFolder(state.sourceFs(), targetFs, state.sourceFolder(), targetFolder);
        List<Entry> pasted = new ArrayList<>();
        List<Entry> failed = new ArrayList<>();
        Exception firstFailure = null;
        for (Entry entry : state.entries()) {
            Operation.checkNotStopped();
            try {
                String targetName = duplicate ? duplicateName(entry.name(), targetFs, targetFolder) : entry.name();
                String targetPath = targetInternalPath(targetFs, targetFolder, targetName, entry.directory());
                if (state.cut()) {
                    state.sourceFs().move(entry.sourceInternalPath(), targetFs, targetPath);
                } else {
                    state.sourceFs().copy(entry.sourceInternalPath(), targetFs, targetPath);
                }
                pasted.add(new Entry(targetName, entry.directory(), entry.sourceInternalPath()));
            } catch (Exception ex) {
                Operation.rethrowIfStopped(ex);
                log.warn("Paste failed for item: {}", entry.name(), ex);
                failed.add(entry);
                firstFailure = firstFailure == null ? ex : firstFailure;
            }
        }
        return new PasteResult(pasted, failed, firstFailure);
    }

    /** {@code name_copy.ext}, then {@code name_copy_2.ext}, {@code name_copy_3.ext}, … until one is not taken. */
    public static String duplicateName(String originalName, Predicate<String> taken) {
        int lastDot = originalName.lastIndexOf('.');
        String name = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
        String extension = lastDot > 0 ? originalName.substring(lastDot) : "";
        String candidate = name + "_copy" + extension;
        for (int counter = 2; taken.test(candidate); counter++) {
            candidate = name + "_copy_" + counter + extension;
        }
        return candidate;
    }

    /** A free {@link #duplicateName} in {@code folder}: an FTP path on FTP, else a folder on disk (archives too). */
    public static String duplicateName(String originalName, VFileSystem fs, String folder) throws IOException {
        if (fs instanceof FtpFileSystem) {
            Set<String> taken = fs.listContents(folder).stream().map(FileItem::getName).collect(Collectors.toSet());
            return duplicateName(originalName, taken::contains);
        }
        return duplicateName(originalName, name -> new File(folder, name).exists());
    }

    public static boolean isSameFolder(VFileSystem sourceFs, VFileSystem targetFs, String sourceFolder, String targetFolder) {
        if (sourceFs == null || targetFs == null || sourceFolder == null || targetFolder == null) {
            return false;
        }
        if (sourceFs instanceof LocalFileSystem && targetFs instanceof LocalFileSystem) {
            try {
                return Paths.get(sourceFolder).toAbsolutePath().normalize().toString()
                        .equalsIgnoreCase(Paths.get(targetFolder).toAbsolutePath().normalize().toString());
            } catch (Exception ex) {
                return sourceFolder.equalsIgnoreCase(targetFolder);
            }
        }
        if (!Objects.equals(sourceFs.getIdentifier(), targetFs.getIdentifier())) {
            return false;
        }
        if (sourceFs instanceof FtpFileSystem) {
            return normalizeFtpFolder(sourceFolder).equals(normalizeFtpFolder(targetFolder));
        }
        return Objects.equals(sourceFolder, targetFolder);
    }

    public static String targetInternalPath(VFileSystem targetFs, String targetFolder, String name, boolean directory) {
        if (targetFs instanceof LocalFileSystem) {
            return targetFs.getInternalPath(new FileItem(Path.of(targetFolder, name)));
        }
        String separator = targetFs.getSeparator();
        String base = targetFolder == null || targetFolder.isBlank() ? separator : targetFolder;
        String fullPath = base.endsWith(separator) ? base + name : base + separator + name;
        if (targetFs instanceof FtpFileSystem ftpFileSystem) {
            return ftpFileSystem.sanitizePath(fullPath);
        }
        return targetFs.getInternalPath(new FileItem(Path.of(fullPath), name, 0, 0, directory));
    }

    private static String normalizeFtpFolder(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
