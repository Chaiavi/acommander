package org.chaiware.acommander.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Defines the access mode for an archive.
 */
@RequiredArgsConstructor
@Getter
public enum ArchiveMode {
    /**
     * Read-write archive: 7z can both extract and update/create entries.
     * Full functionality available - changes are synced back to archive.
     */
    READ_WRITE("Read-Write Archive", true),
    
    /**
     * Read-only archive: 7z can only extract, not modify.
     * Only read operations allowed (view, copy out). Write operations blocked.
     */
    READ_ONLY("Read-Only Archive", false);
    
    private final String displayName;
    private final boolean supportsWrite;
    
    /**
     * Archive extensions that 7-Zip can extract but NOT write to.
     * These are read-only formats.
     */
    private static final Set<String> READ_ONLY_EXTENSIONS = Set.of(
        // Compressed archives (extract only)
        "rar", "arj", "lzh", "lha", "z",

        // Disk images and virtual disk formats
        "iso", "img", "nrg", "vhd", "vhdx", "vmdk", "vdi", "qcow", "qcow2",

        // Installer and package formats
        "msi", "deb", "rpm", "cab", "xar",

        // File systems and specialized formats
        "dmg", "fat", "hfs", "ntfs", "ext", "squashfs", "udf", "apfs", "ar",
        "cpio", "cramfs", "mbr", "gpt", "msjz", "pe", "swm", "uefi",

        // Specialized formats
        "chm", "nsis", "palm", "pcap", "ppmd", "ihex",

        // Split archives (first part)
        "001"
    );

    /**
     * Archive extensions that 7-Zip can both extract and write to.
     */
    private static final Set<String> READ_WRITE_EXTENSIONS = Set.of(
        "7z", "zip", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "wim"
    );
    
    /**
     * Determines the archive mode based on file extension.
     */
    public static ArchiveMode fromExtension(String extension) {
        if (extension == null) {
            return READ_ONLY;
        }
        String ext = extension.toLowerCase();
        if (READ_ONLY_EXTENSIONS.contains(ext)) {
            return READ_ONLY;
        }
        // Default to read-write for known writable formats
        if (READ_WRITE_EXTENSIONS.contains(ext)) {
            return READ_WRITE;
        }
        // For unknown extensions, try to determine by checking read-only list
        return READ_ONLY_EXTENSIONS.contains(ext) ? READ_ONLY : READ_WRITE;
    }
    
    /**
     * Checks if the given extension is a read-only archive format.
     */
    public static boolean isReadOnlyExtension(String extension) {
        return fromExtension(extension) == READ_ONLY;
    }
    
    /**
     * Checks if the given extension is a read-write archive format.
     */
    public static boolean isReadWriteExtension(String extension) {
        return fromExtension(extension) == READ_WRITE;
    }
}
