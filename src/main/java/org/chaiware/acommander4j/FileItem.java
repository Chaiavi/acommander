package org.chaiware.acommander4j;

import java.io.File;

public class FileItem {
    private final File file;
    private String presentableFilename;

    public FileItem(File file) {
        this.file = file;
        presentableFilename = file.getName();
    }

    public FileItem(File folder, String filenameStr) {
        this(folder);
        this.presentableFilename = filenameStr;
    }

    public String getName() {
        return file.getName();
    }

    public String getFullPath() {
        return file.getAbsolutePath();
    }

    public long getSize() {
        return file.length();
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public File getFile() {
        return file;
    }

    public String getPresentableFilename() {
        return presentableFilename;
    }

    @Override
    public String toString() {
        return presentableFilename; // Display name in ListView
    }
}
