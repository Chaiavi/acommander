package org.chaiware.acommander4j;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BasicActionsImpl implements IActions {
    @Override
    public void edit(FileItem fileItem) throws IOException {
        runExecutable("C:\\Users\\User\\IdeaProjects\\acommander4j\\apps\\TedNPad.exe", fileItem.getFile().toString());
    }

    @Override
    public void view(FileItem fileItem) throws IOException {
        runExecutable("C:\\Users\\User\\Desktop\\tmp\\ql\\QuickLook.exe", fileItem.getFile().toString()); // QuickLook Best but 236mb
        runExecutable("C:\\Users\\User\\Desktop\\tmp\\uv\\Viewer.exe", fileItem.getFile().toString()); // UniversalViewer least features but 10mb
        runExecutable("C:\\Users\\User\\Desktop\\tmp\\fvl\\fv.exe", fileItem.getFile().toString()); // FileViewerLite 98mb quite good
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws IOException {
        runExecutable("C:\\Users\\User\\Desktop\\tmp\\fc2\\FastCopy.exe", sourceFile.getFile().toString(), "/to=" + targetFolder); // QuickLook Best but 236mb
    }

    private void runExecutable(String executable, String filename) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(executable, filename);
        pb.directory(new File(filename).getParentFile());
        pb.start();
    }

    private void runExecutable(String executable, String filename, String folderName) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(executable, filename, folderName);
        pb.directory(new File(filename).getParentFile());
        pb.start();
    }
}
