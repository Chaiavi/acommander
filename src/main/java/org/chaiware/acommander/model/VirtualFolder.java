package org.chaiware.acommander.model;

public class VirtualFolder extends Folder {
    private final String displayPath;
    private final String archivePath;
    private final boolean root;

    public VirtualFolder(String realPath, String displayPath, String archivePath, boolean root) {
        super(realPath);
        this.displayPath = displayPath;
        this.archivePath = archivePath;
        this.root = root;
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public boolean isRoot() {
        return root;
    }

    @Override
    public String toString() {
        return displayPath;
    }
}
