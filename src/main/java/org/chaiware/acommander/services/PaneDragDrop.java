package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Drag and drop between the panes and other apps: which files a drag carries and where a drop goes. */
public final class PaneDragDrop {

    private PaneDragDrop() {
    }

    /** The real files behind local and archive pane items (an archive pane shows its extracted folder); ".." dropped. */
    public static List<File> filesOnDisk(List<FileItem> items) {
        return items.stream()
                .filter(item -> !"..".equals(item.getPresentableFilename()) && item.getPath() != null)
                .map(item -> item.getPath().toAbsolutePath().toFile())
                .toList();
    }

    /** Copies the dragged FTP items into {@code folder} under their own names. Blocking. */
    public static List<File> download(ClipboardTransfer.State state, Path folder) throws IOException {
        ClipboardTransfer.PasteResult result = ClipboardTransfer.paste(state, new LocalFileSystem(""), folder.toString());
        if (!result.failed().isEmpty()) {
            String names = result.failed().stream().map(ClipboardTransfer.Entry::name).collect(Collectors.joining(", "));
            throw new IOException("Could not download " + names, result.firstFailure());
        }
        return result.pasted().stream().map(entry -> folder.resolve(entry.name()).toFile()).toList();
    }

    /** False for archives: another app moving an extracted file would leave the archive unaware of it. */
    public static boolean allowsMoveOut(VFileSystem fs) {
        return !(fs instanceof ArchiveFileSystem);
    }

    /** Files dropped from another app, as a copy from the local disk. */
    public static ClipboardTransfer.State fromDroppedFiles(List<File> files, FilesPanesHelper.FocusSide targetSide) {
        List<ClipboardTransfer.Entry> entries = files.stream()
                .map(file -> new ClipboardTransfer.Entry(file.getName(), file.isDirectory(), file.getAbsolutePath()))
                .toList();
        String sourceFolder = files.isEmpty() ? null : files.getFirst().getAbsoluteFile().getParent();
        return new ClipboardTransfer.State(entries, false, targetSide, new LocalFileSystem(""), sourceFolder);
    }

    /** The folder a drop onto {@code folderRow} goes into, in the form the pane uses when it opens that folder. */
    public static String folderOf(VFileSystem fs, FileItem folderRow) {
        return fs instanceof FtpFileSystem ? fs.getInternalPath(folderRow) : folderRow.getFullPath();
    }

    /** True when {@code row}, a folder in {@code paneFolder}, is itself being dragged: it can't go into itself. */
    public static boolean isDraggedFolder(ClipboardTransfer.State state, VFileSystem targetFs, String paneFolder, FileItem row) {
        return ClipboardTransfer.isSameFolder(state.sourceFs(), targetFs, state.sourceFolder(), paneFolder)
                && state.entries().stream().anyMatch(entry -> entry.name().equalsIgnoreCase(row.getName()));
    }
}
