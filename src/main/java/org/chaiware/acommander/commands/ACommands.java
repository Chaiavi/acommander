package org.chaiware.acommander.commands;

import javafx.application.Platform;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class ACommands {
    protected final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    protected FilesPanesHelper fileListsLoader;
    protected ExternalCommandListener externalCommandListener;
    private final Set<Process> runningProcesses = ConcurrentHashMap.newKeySet();
    final Logger log = LoggerFactory.getLogger(ACommands.class);

    public ACommands(FilesPanesHelper filesPanesHelper) {
        this.fileListsLoader = filesPanesHelper;
    }

    public void setExternalCommandListener(ExternalCommandListener externalCommandListener) {
        this.externalCommandListener = externalCommandListener;
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
        List<String> commandSnapshot = List.copyOf(params);
        notifyCommandStarted(commandSnapshot);
        return CompletableFuture.supplyAsync(() -> {
            int exitCode = -1;
            Throwable failure = null;
            Process process = null;
            List<String> output = List.of();
            try {
                ProcessBuilder pb = new ProcessBuilder(params);
                pb.redirectErrorStream(true); // merges stderr into stdout
                log.debug("Running: {}", String.join(" ", pb.command()));
                process = pb.start();
                runningProcesses.add(process);

                output = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                }

                exitCode = process.waitFor();
                log.debug("Process completed with exit code: {}", exitCode);
                if (exitCode != 0) {
                    String toolOutput = summarizeOutput(output);
                    IllegalStateException ex = new IllegalStateException(
                            "External command failed with exit code " + exitCode + ": " + formatCommand(commandSnapshot)
                    );
                    failure = ex;
                    log.error(
                            "External command failed. exitCode={} command={} outputTail={}",
                            exitCode,
                            formatCommand(commandSnapshot),
                            toolOutput
                    );
                    throw ex;
                }

                if (shouldUpdateUI) Platform.runLater(() -> fileListsLoader.refreshFileListViews());
                return output;

            } catch (IOException | InterruptedException e) {
                failure = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("Error running external process. command={}", formatCommand(commandSnapshot), e);
                throw new RuntimeException(e);
            } finally {
                if (process != null) {
                    runningProcesses.remove(process);
                }
                notifyCommandFinished(commandSnapshot, exitCode, failure);
            }
        });
    }

    public CompletableFuture<List<String>> runExternal(List<String> params, boolean shouldUpdateUI) {
        return runExecutable(params, shouldUpdateUI);
    }

    public int stopRunningExternalCommands() {
        List<Process> snapshot = new ArrayList<>(runningProcesses);
        int stopped = 0;
        for (Process process : snapshot) {
            if (!process.isAlive()) {
                continue;
            }
            try {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                stopped++;
            } catch (Exception ex) {
                log.warn("Failed stopping external process", ex);
            }
        }
        return stopped;
    }

    private void notifyCommandStarted(List<String> command) {
        if (externalCommandListener == null) {
            return;
        }
        try {
            externalCommandListener.onCommandStarted(command);
        } catch (Exception ex) {
            log.debug("External command listener failed on start", ex);
        }
    }

    private void notifyCommandFinished(List<String> command, int exitCode, Throwable error) {
        if (externalCommandListener == null) {
            return;
        }
        try {
            externalCommandListener.onCommandFinished(command, exitCode, error);
        } catch (Exception ex) {
            log.debug("External command listener failed on finish", ex);
        }
    }

    private String formatCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "<empty>";
        }
        return String.join(" ", command);
    }

    private String summarizeOutput(List<String> output) {
        if (output == null || output.isEmpty()) {
            return "<no output>";
        }
        int start = Math.max(0, output.size() - 20);
        String tail = String.join(" | ", output.subList(start, output.size()));
        if (tail.length() > 4000) {
            return tail.substring(tail.length() - 4000);
        }
        return tail;
    }
}
