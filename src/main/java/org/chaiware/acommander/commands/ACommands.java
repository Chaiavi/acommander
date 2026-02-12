package org.chaiware.acommander.commands;

import javafx.application.Platform;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class ACommands {
    protected final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    protected FilesPanesHelper fileListsLoader;
    final Logger log = LoggerFactory.getLogger(ACommands.class);

    public ACommands(FilesPanesHelper filesPanesHelper) {
        this.fileListsLoader = filesPanesHelper;
    }

    // helper methods for filtering
    public List<FileItem> filterValidItems(List<FileItem> items) {
        return items.stream()
                .filter(item -> !isParentFolder(item))
                .collect(Collectors.toList());
    }

    private boolean isParentFolder(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }

    private boolean isValidSingleItem(FileItem item) {
        return !isParentFolder(item);
    }

    // PUBLIC METHODS - These handle filtering automatically
    public final void rename(List<FileItem> selectedItems, String newFilename) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doRename(validItems, newFilename);
        }
    }

    public final void edit(FileItem fileItem) throws Exception {
        if (isValidSingleItem(fileItem)) {
            doEdit(fileItem);
        }
    }

    public final void view(FileItem fileItem) throws Exception {
        if (isValidSingleItem(fileItem)) {
            doView(fileItem);
        }
    }

    public final void copy(FileItem sourceFile, String targetFolder) throws Exception {
        if (isValidSingleItem(sourceFile)) {
            doCopy(sourceFile, targetFolder);
        }
    }

    public final void move(FileItem sourceFile, String targetFolder) throws Exception {
        if (isValidSingleItem(sourceFile)) {
            doMove(sourceFile, targetFolder);
        }
    }

    public final void delete(List<FileItem> selectedItems) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doDelete(validItems);
        }
    }

    public final void wipeDelete(List<FileItem> selectedItems) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doWipeDelete(validItems);
        }
    }

    public final void unlockDelete(List<FileItem> selectedItems) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doUnlockDelete(validItems);
        }
    }

    public final void pack(List<FileItem> selectedItems, String archiveFilenameWithPath) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doPack(validItems, archiveFilenameWithPath);
        }
    }

    public final void unpack(FileItem selectedItem, String destinationPath) throws Exception {
        if (isValidSingleItem(selectedItem)) {
            doUnpack(selectedItem, destinationPath);
        }
    }

    public final void extractAll(FileItem selectedItem, String destinationPath) throws Exception {
        if (isValidSingleItem(selectedItem)) {
            doExtractAll(selectedItem, destinationPath);
        }
    }

    public final void mergePDFs(List<FileItem> selectedItems, String newPdfFilenameWithPath) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (!validItems.isEmpty()) {
            doMergePDFs(validItems, newPdfFilenameWithPath);
        }
    }

    public final void extractPDFPages(FileItem selectedItem, String destinationPath) throws Exception {
        if (isValidSingleItem(selectedItem)) {
            doExtractPDFPages(selectedItem, destinationPath);
        }
    }

    // These methods don't need filtering as they don't operate on selected files
    public abstract void mkdir(String parentDir, String newDirName) throws IOException;
    public abstract void mkFile(String focusedPath, String newFileName) throws Exception;
    public abstract void openTerminal(String openHerePath) throws Exception;
    public abstract void openExplorer(String openHerePath) throws Exception;
    public abstract void searchFiles(String sourcePath, String filenameWildcard) throws Exception;

    // ABSTRACT METHODS - Subclasses implement these (they receive pre-filtered items)
    protected abstract void doRename(List<FileItem> validItems, String newFilename) throws Exception;
    protected abstract void doEdit(FileItem fileItem) throws Exception;
    protected abstract void doView(FileItem fileItem) throws Exception;
    protected abstract void doCopy(FileItem sourceFile, String targetFolder) throws Exception;
    protected abstract void doMove(FileItem sourceFile, String targetFolder) throws Exception;
    protected abstract void doDelete(List<FileItem> validItems) throws Exception;
    protected abstract void doWipeDelete(List<FileItem> validItems) throws Exception;
    protected abstract void doUnlockDelete(List<FileItem> validItems) throws Exception;
    protected abstract void doPack(List<FileItem> validItems, String archiveFilenameWithPath) throws Exception;
    protected abstract void doUnpack(FileItem selectedItem, String destinationPath) throws Exception;
    protected abstract void doExtractAll(FileItem selectedItem, String destinationPath) throws Exception;
    protected abstract void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) throws Exception;
    protected abstract void doExtractPDFPages(FileItem selectedItem, String destinationPath) throws Exception;

    protected CompletableFuture<List<String>> runExecutable(List<String> params, boolean shouldUpdateUI) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(params);
                pb.redirectErrorStream(true); // merges stderr into stdout
                log.debug("Running: {}", String.join(" ", pb.command()));
                Process process = pb.start();

                List<String> output = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                }

                int exitCode = process.waitFor();
                log.debug("Process completed with exit code: {}", exitCode);

                if (shouldUpdateUI) Platform.runLater(() -> fileListsLoader.refreshFileListViews());
                return output;

            } catch (IOException | InterruptedException e) {
                log.error("Error running external process", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<String>> runExternal(List<String> params, boolean shouldUpdateUI) {
        return runExecutable(params, shouldUpdateUI);
    }
}
