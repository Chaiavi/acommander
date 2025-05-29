package org.chaiware.acommander4j;

import java.io.IOException;

public interface IActions {
    void edit(FileItem fileItem) throws Exception;
    void view(FileItem fileItem) throws IOException;
    void copy(FileItem sourceFile, String targetFolder) throws Exception;
}
