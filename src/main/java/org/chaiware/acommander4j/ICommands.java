package org.chaiware.acommander4j;

import java.io.IOException;

public interface ICommands {
    void edit(FileItem fileItem) throws Exception;
    void view(FileItem fileItem) throws Exception;
    void copy(FileItem sourceFile, String targetFolder) throws Exception;
    void move(FileItem sourceFile, String targetFolder) throws Exception;
}
