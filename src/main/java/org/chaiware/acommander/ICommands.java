package org.chaiware.acommander;

import java.io.IOException;

public interface ICommands {
    void rename(FileItem selectedItem, String newFilename) throws Exception;
    void edit(FileItem fileItem) throws Exception;
    void view(FileItem fileItem) throws Exception;
    void copy(FileItem sourceFile, String targetFolder) throws Exception;
    void move(FileItem sourceFile, String targetFolder) throws Exception;
    void mkdir(String parentDir, String newDirName) throws IOException;
    void delete(FileItem selectedItem) throws IOException;
    void openTerminal(String openHerePath) throws Exception;
    void pack(FileItem selectedItem);
    void unpack(FileItem selectedItem);

}
