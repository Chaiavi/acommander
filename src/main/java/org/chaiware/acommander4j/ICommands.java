package org.chaiware.acommander4j;

public interface ICommands {
    void edit(FileItem fileItem) throws Exception;
    void view(FileItem fileItem) throws Exception;
    void copy(FileItem sourceFile, String targetFolder) throws Exception;
    void move(FileItem sourceFile, String targetFolder) throws Exception;
    void rename(FileItem selectedItem);
    void pack(FileItem selectedItem);
    void unpack(FileItem selectedItem);
}
