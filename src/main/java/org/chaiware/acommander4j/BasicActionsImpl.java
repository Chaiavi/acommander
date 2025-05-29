package org.chaiware.acommander4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BasicActionsImpl implements IActions {
    private static final Logger log = LoggerFactory.getLogger(BasicActionsImpl.class);
    private final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    private final FileListsLoader fileListsLoader;

    public BasicActionsImpl(FileListsLoader fileListsLoader) {
        this.fileListsLoader = fileListsLoader;
    }

    @Override
    public void view(FileItem fileItem) throws IOException {
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\ql\\QuickLook.exe", fileItem.getFile().toString()); // QuickLook Best but 236mb
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\uv\\Viewer.exe", fileItem.getFile().toString()); // UniversalViewer least features but 10mb
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\fvl\\fv.exe", fileItem.getFile().toString()); // FileViewerLite 98mb quite good
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {
        log.info("Editing: {}", fileItem.getName());
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "TedNPad.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command);
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {
        log.info("Copying: {} To: {}", sourceFile, targetFolder);
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "fastcopy\\FastCopy.exe");
        command.add("/cmd=diff");
        command.add("/auto_close");
        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command);
    }

    private void runExecutable(List<String> params) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(params);
//        pb.directory(new File(filename).getParentFile());
        log.info("Running: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        process.waitFor();
        fileListsLoader.refreshFileListViews();
    }
}
