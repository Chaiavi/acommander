package org.chaiware.acommander4j;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BasicActionsImpl implements IActions {
    private final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";

    @Override
    public void view(FileItem fileItem) throws IOException {
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\ql\\QuickLook.exe", fileItem.getFile().toString()); // QuickLook Best but 236mb
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\uv\\Viewer.exe", fileItem.getFile().toString()); // UniversalViewer least features but 10mb
//        runExecutable("C:\\Users\\User\\Desktop\\tmp\\fvl\\fv.exe", fileItem.getFile().toString()); // FileViewerLite 98mb quite good
    }

    @Override
    public void edit(FileItem fileItem) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "TedNPad.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command);
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "FastCopy.exe");
        command.add("/cmd=diff");
        command.add("/auto_close");
        command.add("/force_close");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command);
         // fastcopy.exe /cmd=diff /auto_close /force_close /no_confirm_stop "FastCopy.exe" /to=".\doc"
    }

    private void runExecutable(List<String> params) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(params);
//        pb.directory(new File(filename).getParentFile());
        pb.start();
    }
}
