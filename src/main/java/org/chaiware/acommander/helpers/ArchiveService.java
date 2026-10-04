package org.chaiware.acommander.helpers;

import java.util.List;

/** Extensions 7-Zip can unpack. Wider than {@link org.chaiware.acommander.model.ArchiveMode}, which lists only browsable ones. */
public final class ArchiveService {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
        "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "lzma", "cab",
        "iso", "img", "vhd", "wim", "swm", "esd", "fat", "ntfs", "vmdk", "qcow2",
        "arj", "chm", "cpio", "cramfs", "deb", "dmg", "elf", "ext", "gpt", "hfs",
        "ihex", "lzh", "lzma86", "mbr", "msi", "nsis", "palm", "pcap", "pe",
        "ppmd", "rpm", "squashfs", "uefi", "vdi", "xar", "z", "zipx"
    );

    private ArchiveService() {
    }

    public static boolean isSupportedArchiveExtension(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
}
