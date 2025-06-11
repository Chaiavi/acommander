package org.chaiware.acommander;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CommandsSimpleImpl implements ICommands {
    private static final Logger log = LoggerFactory.getLogger(CommandsSimpleImpl.class);
    FilesPanesHelper filesPanesHelper;

    public CommandsSimpleImpl(FilesPanesHelper filesPanesHelper) {
        this.filesPanesHelper = filesPanesHelper;
    }

    @Override
    public void rename(FileItem selectedItem, String newFilename) throws Exception {
        selectedItem.getFile().renameTo(new File(selectedItem.getFile().getParent(), newFilename));
        log.debug("Renamed: {} to {}", selectedItem.getName(), newFilename);
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {

    }

    @Override
    public void view(FileItem fileItem) throws Exception {

    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {

    }

    @Override
    public void move(FileItem sourceFile, String targetFolder) throws Exception {
        Path source = Paths.get(sourceFile.getFullPath());
        Path target = Paths.get(targetFolder + "\\" + sourceFile.getName());

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        filesPanesHelper.refreshFileListViews();
        log.debug("Moved: {} to {}", sourceFile.getName(), targetFolder);
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {

    }

    @Override
    public void delete(FileItem selectedItem) throws IOException {

    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {

    }

    @Override
    public void searchFiles(String sourcePath, String filenameWildcard) throws Exception {

    }

    @Override
    public void pack(FileItem selectedItem, String archiveFilename, String destinationPath) throws Exception {

    }

    @Override
    public void unpack(FileItem selectedItem, String destinationPath) throws Exception {

    }
}
