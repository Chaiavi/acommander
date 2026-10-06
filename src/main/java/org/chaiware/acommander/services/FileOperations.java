package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.commands.Operation;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.services.ClipboardTransfer.PasteResult;
import org.chaiware.acommander.tools.ProcessRunner;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Rename, copy, move, delete, new folder / file, view, edit, terminal and explorer. Every method gets the file system
 * and paths the user picked when the operation started ({@link ClipboardTransfer#capture}), never the panes, so
 * switching panes while it runs changes nothing. The blocking ones run off the FX thread; the caller refreshes.
 */
public class FileOperations {
    private static final Logger log = LoggerFactory.getLogger(FileOperations.class);

    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public FileOperations(AppRegistry registry, ExternalToolRunner runner) {
        this.registry = registry;
        this.runner = runner;
    }

    /** {@code items} without the ".." entry. */
    public List<FileItem> filterValidItems(List<FileItem> items) {
        return items.stream().filter(item -> !isParentEntry(item)).collect(Collectors.toList());
    }

    /** Renames {@code entry} in its folder. Blocking. */
    public void rename(VFileSystem fs, Entry entry, String newFilename) throws IOException {
        String oldInternalPath = entry.sourceInternalPath();
        int lastSeparator = Math.max(oldInternalPath.lastIndexOf('/'), oldInternalPath.lastIndexOf('\\'));
        fs.rename(oldInternalPath, oldInternalPath.substring(0, lastSeparator + 1) + newFilename);
        log.debug("Renamed: {} to {}", entry.name(), newFilename);
    }

    /** Opens the multi-rename tool on local items. */
    public void multiRename(List<FileItem> items) {
        runner.reportFailure(runner.runExecutable(command("multiRename", Map.of(), fullPaths(filterValidItems(items))), true), "Multi Rename");
    }

    /** Opens the viewer; an archive or FTP file is viewed from a temp copy. Blocking while that copy is made. */
    public void view(VFileSystem fs, Entry entry) throws IOException {
        Path fileToView = localCopy(fs, entry, "acommander_view_");
        boolean isTemp = !(fs instanceof LocalFileSystem);
        runner.reportFailure(runner.runExecutable(command("view", Map.of(), List.of(fileToView.toString())), false)
                .thenRun(() -> {
                    if (isTemp) {
                        FileHelper.deleteQuietly(fileToView);
                    }
                }), "View");
        log.debug("Viewed: {}", entry.name());
    }

    /**
     * Opens the editor; an archive or FTP file is edited in a temp copy that is saved back when the editor closes.
     * Until then the copy is kept across exits ({@link AppTempDir#retain}), and it stays if saving back fails.
     */
    public void edit(VFileSystem fs, Entry entry) throws IOException {
        Path fileToEdit = localCopy(fs, entry, "acommander_edit_");
        boolean isTemp = !(fs instanceof LocalFileSystem);
        if (isTemp) {
            AppTempDir.retain(fileToEdit);
        }
        String internalPath = entry.sourceInternalPath();
        runner.reportFailure(runner.runExecutable(command("edit", Map.of(), List.of(fileToEdit.toString())), false)
                .thenRun(() -> {
                    if (isTemp) {
                        try {
                            new LocalFileSystem("").copy(fileToEdit.toString(), fs, internalPath);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Your edit could not be saved to " + internalPath
                                    + ". The edited copy is kept at " + fileToEdit + ", also after ACommander closes.", e);
                        }
                        FileHelper.deleteQuietly(fileToEdit);
                        AppTempDir.release(fileToEdit);
                    }
                    fs.markModified();
                }), "Edit");
        log.debug("Edited: {}", entry.name());
    }

    /**
     * Copies (or, for a cut, moves) every entry of {@code source} into {@code targetFolder}. Blocking. Local to local
     * runs FastCopy (a move on one drive is a rename); archive and FTP sides go through the VFS, and a copy into its
     * own folder gets {@link ClipboardTransfer#duplicateName duplicate names}. A failed item is listed, the rest run.
     * {@code policy} decides what happens to the {@code conflicts} ({@link TransferConflicts#find}).
     */
    public PasteResult transfer(ClipboardTransfer.State source, VFileSystem targetFs, String targetFolder,
                                TransferConflicts.Policy policy, List<TransferConflicts.Conflict> conflicts) {
        boolean bothLocal = source.sourceFs() instanceof LocalFileSystem && targetFs instanceof LocalFileSystem;
        if (bothLocal && !source.cut()
                && !ClipboardTransfer.isSameFolder(source.sourceFs(), targetFs, source.sourceFolder(), targetFolder)) {
            return copyLocal(source.entries(), targetFolder, policy);
        }
        List<Entry> kept = TransferConflicts.keep(source.entries(), conflicts, policy);
        if (bothLocal && source.cut()) {
            return moveLocal(kept, targetFolder);
        }
        return ClipboardTransfer.paste(new ClipboardTransfer.State(kept, source.cut(), source.sourceSide(),
                source.sourceFs(), source.sourceFolder()), targetFs, targetFolder);
    }

    /** One FastCopy run for all entries, then a check: FastCopy can exit 0 and still skip files. */
    private PasteResult copyLocal(List<Entry> entries, String targetFolder, TransferConflicts.Policy policy) {
        List<String> sources = entries.stream().map(Entry::sourceInternalPath).toList();
        Map<String, String> values = Map.of("${targetFolder}", toolTarget(targetFolder), "${copyMode}", policy.fastCopyMode());
        try {
            runner.runExecutable(command("copy", values, sources), false).join();
        } catch (CompletionException e) {
            Operation.rethrowIfStopped(e);
            return new PasteResult(List.of(), entries, unwrap(e));
        }
        return checkArrived(entries, targetFolder, "The copy tool reported success, but these are missing in ");
    }

    /** Item by item: a rename on the same drive, FastCopy across drives or into an existing folder (it merges). */
    private PasteResult moveLocal(List<Entry> entries, String targetFolder) {
        List<Entry> moved = new ArrayList<>();
        List<Entry> failed = new ArrayList<>();
        Exception firstFailure = null;
        LocalFileSystem local = new LocalFileSystem("");
        for (Entry entry : entries) {
            Operation.checkNotStopped();
            try {
                if (sameDrive(entry.sourceInternalPath(), targetFolder)
                        && !(entry.directory() && Files.isDirectory(Paths.get(targetFolder, entry.name())))) {
                    local.move(entry.sourceInternalPath(), local, Paths.get(targetFolder, entry.name()).toString());
                } else {
                    runner.runExecutable(command("move", Map.of("${targetFolder}", toolTarget(targetFolder)),
                            List.of(entry.sourceInternalPath())), false).join();
                }
                moved.add(entry);
            } catch (Exception e) {
                Operation.rethrowIfStopped(e);
                Exception cause = unwrap(e);
                log.error("Move failed: {} -> {}", entry.sourceInternalPath(), targetFolder, cause);
                failed.add(entry);
                firstFailure = firstFailure == null ? cause : firstFailure;
            }
        }
        PasteResult arrived = checkArrived(moved, targetFolder, "The move tool reported success, but these are missing in ");
        failed.addAll(arrived.failed());
        return new PasteResult(arrived.pasted(), failed, firstFailure != null ? firstFailure : arrived.firstFailure());
    }

    private static PasteResult checkArrived(List<Entry> entries, String targetFolder, String message) {
        Path target = Paths.get(targetFolder);
        List<Entry> arrived = new ArrayList<>();
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : entries) {
            (Files.exists(target.resolve(entry.name())) ? arrived : missing).add(entry);
        }
        if (missing.isEmpty()) {
            return new PasteResult(arrived, missing, null);
        }
        String names = missing.stream().map(Entry::name).collect(Collectors.joining(", "));
        log.error("{}{}: {}", message, targetFolder, names);
        return new PasteResult(arrived, missing, new IOException(message + targetFolder + ": " + names));
    }

    public void mkdir(VFileSystem fs, String parentDir, String newDirName) throws IOException {
        fs.makeDirectory(ClipboardTransfer.targetInternalPath(fs, parentDir, newDirName, true));
        log.debug("Created Directory: {}", newDirName);
    }

    public void mkFile(VFileSystem fs, String parentDir, String newFileName) throws IOException {
        fs.makeFile(ClipboardTransfer.targetInternalPath(fs, parentDir, newFileName, false));
        log.debug("Created File: {}", newFileName);
    }

    /**
     * Deletes the entries through the VFS. Blocking. Local items that fail (locked) go to the unlock-and-delete tool;
     * returns the ones that failed.
     */
    public List<Entry> delete(VFileSystem fs, List<Entry> entries) {
        List<Entry> failed = new ArrayList<>();
        for (Entry entry : entries) {
            Operation.checkNotStopped();
            try {
                fs.delete(entry.sourceInternalPath());
                log.info("Deleted: {}", entry.sourceInternalPath());
            } catch (Exception e) {
                Operation.rethrowIfStopped(e);
                log.error("Failed deleting: {}", entry.sourceInternalPath(), e);
                failed.add(entry);
            }
        }
        if (!failed.isEmpty() && fs instanceof LocalFileSystem) {
            log.info("Failed to delete {} files, attempting to unlock them so you can delete them all", failed.size());
            runner.reportFailure(runner.runExecutable(command("unlockDelete", Map.of(),
                    failed.stream().map(Entry::sourceInternalPath).toList()), true), "Unlock and Delete");
        }
        return failed;
    }

    /** Overwrites, then deletes (SDelete). */
    public void wipeDelete(List<FileItem> items) {
        List<FileItem> validItems = filterValidItems(items);
        if (validItems.isEmpty()) {
            return;
        }
        runner.reportFailure(runner.runExecutable(command("wipeDelete", Map.of(), fullPaths(validItems)), true), "Wipe Delete");
        log.debug("Deleted & Wiped: {}", validItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    /** Opens PowerShell in {@code folder}; the folder is only the working directory, so its name can't run as code. */
    public void openTerminal(String folder) throws IOException {
        // Java gives a GUI app's console children no window: a hidden PowerShell starts the visible one.
        ProcessRunner.of("powershell", "-NoProfile", "-Command", "Start-Process powershell -ArgumentList '-NoExit'")
                .directory(new File(folder))
                .launch();
        log.debug("Opened PowerShell Here: {}", folder);
    }

    /** Opens the hosts file in the apps.json editor as administrator (UAC prompt). */
    public void openHostsFile() throws IOException {
        Path hosts = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "drivers", "etc", "hosts");
        if (!Files.isRegularFile(hosts)) {
            throw new FileNotFoundException("Could not find the hosts file at " + hosts);
        }
        ActionDefinition editAction = registry.findAction("edit")
                .orElseThrow(() -> new IllegalStateException("Missing action config: edit"));
        String args = String.join(" ", editAction.getArgs()).replace("${selectedFile}", hosts.toString());
        ProcessRunner.of("powershell", "-NoProfile", "-Command",
                "Start-Process -FilePath '" + editAction.getPath() + "' -ArgumentList '" + args + "' -Verb RunAs").launch();
    }

    /** Opens Explorer on the folder (a file's own folder); explorer.exe exits 1 even when it worked. */
    public void openExplorer(String openHerePath) {
        if (openHerePath == null || openHerePath.isBlank()) {
            log.warn("Open Explorer Here skipped: path is blank");
            return;
        }
        File target = new File(openHerePath);
        if (target.isFile()) {
            target = target.getParentFile();
        }
        if (target == null || !target.exists()) {
            log.warn("Open Explorer Here skipped: path does not exist: {}", openHerePath);
            return;
        }
        runner.reportFailure(runner.runExecutable(List.of("explorer.exe", target.getAbsolutePath()), false, Set.of(1)), "Open Explorer");
    }

    /**
     * FastCopy's /to= folder with a trailing backslash: without it, FastCopy copies or moves a single folder's
     * contents into the target instead of the folder itself.
     */
    static String toolTarget(String targetFolder) {
        return targetFolder.endsWith("\\") ? targetFolder : targetFolder + "\\";
    }

    private static boolean sameDrive(String sourcePath, String targetFolder) {
        try {
            return Paths.get(sourcePath).getRoot().toString().equalsIgnoreCase(Paths.get(targetFolder).getRoot().toString());
        } catch (Exception e) {
            return false;
        }
    }

    private static Exception unwrap(Exception e) {
        return e instanceof CompletionException && e.getCause() instanceof Exception cause ? cause : e;
    }

    private static Path localCopy(VFileSystem fs, Entry entry, String prefix) throws IOException {
        if (fs instanceof LocalFileSystem) {
            return Path.of(entry.sourceInternalPath()).toAbsolutePath();
        }
        Path copy = AppTempDir.createTempFile(prefix, "_" + entry.name());
        fs.copy(entry.sourceInternalPath(), new LocalFileSystem(""), copy.toString());
        return copy;
    }

    private List<String> command(String actionId, Map<String, String> values, List<String> selectedFiles) {
        ActionDefinition action = registry.requireAction(actionId);
        return ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), null, values, selectedFiles);
    }

    private static List<String> fullPaths(List<FileItem> items) {
        return items.stream().map(FileItem::getFullPath).collect(Collectors.toList());
    }

    private static boolean isParentEntry(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }
}
