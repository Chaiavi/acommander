package org.chaiware.acommander4j;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BasicActionsImpl implements IActions {
    @Override
    public void edit(FileItem fileItem) throws IOException {
        runExecutable(Collections.singletonList(fileItem.getFile().toString()));
    }

    private void runExecutable(List<String> args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("C:\\Users\\User\\IdeaProjects\\acommander4j\\apps\\TedNPad.exe", args.get(0));
        pb.directory(new File(args.get(0)).getParentFile());
        pb.start();
    }
}
