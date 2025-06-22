package org.chaiware.acommander.commands;

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

public abstract class ACommands {
    protected final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    protected FilesPanesHelper fileListsLoader;
    final Logger log = LoggerFactory.getLogger(ACommands.class);

    public abstract void rename(List<FileItem> selectedItem, String newFilename) throws Exception;
    public abstract void edit(FileItem fileItem) throws Exception;
    public abstract void view(FileItem fileItem) throws Exception;
    public abstract void copy(FileItem sourceFile, String targetFolder) throws Exception;
    public abstract void move(FileItem sourceFile, String targetFolder) throws Exception;
    public abstract void mkdir(String parentDir, String newDirName) throws IOException;
    public abstract void mkFile(String focusedPath, String newFileName) throws Exception;
    public abstract void delete(List<FileItem> selectedItems) throws Exception;
    public abstract void unlockDelete(List<FileItem> selectedItems) throws Exception;
    public abstract void openTerminal(String openHerePath) throws Exception;
    public abstract void searchFiles(String sourcePath, String filenameWildcard) throws Exception;
    public abstract void pack(List<FileItem> selectedItem, String archiveFilename, String destinationPath) throws Exception;
    public abstract void unpack(FileItem selectedItem, String destinationPath) throws Exception;
    public abstract void extractAll(FileItem selectedItem, String destinationPath) throws Exception;

    public ACommands(FilesPanesHelper filesPanesHelper) {
        this.fileListsLoader = filesPanesHelper;
    }

    protected List<String> runExecutable(List<String> params, boolean isWaitFor) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(params);
        pb.redirectErrorStream(true); // merges stderr into stdout
//        pb.directory(new File("C:\\Users\\Hayun\\IdeaProjects\\acommander\\apps"));
        log.debug("Running: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        if (isWaitFor) {
            process.waitFor();
            fileListsLoader.refreshFileListViews();
        }

        return output;
    }
}
