package org.chaiware.acommander.tools;

import org.chaiware.acommander.helpers.AppPaths;

import java.nio.file.Path;

/** Tools under apps/ that the code runs directly. Tools run by an apps.json action are listed there instead. */
public enum BundledTool {
    SEVEN_ZIP("apps/extract_all/UniExtract/bin/x64/7z.exe"),
    CURL("apps/remote_connectivity/curl.exe"),
    EXIV2("apps/image_metadata/exiv2.exe"),
    ATOMIC_PARSLEY("apps/video_metadata/AtomicParsley.exe"),
    ID3("apps/audio_metadata/id3.exe"),
    RIPGREP("apps/search_in_files/rg.exe"),
    CAESIUM("apps/image_convert/caesiumclt.exe"),
    SNDFILE_CONVERT("apps/sound_convert/sndfile-convert.exe"),
    FAAC("apps/sound_convert/faac.exe"),
    FAAD("apps/sound_convert/faad.exe"),
    FILE("apps/file_analysis/file.exe"),
    FILE_MAGIC("apps/file_analysis/magic.mgc"),
    RHASH("apps/checksum/rhash.exe"),
    EXAM_DIFF("apps/file_compare/ExamDiff.exe"),
    UPX("apps/exe_compress/upx.exe"),
    VIEWER("apps/view/UniversalViewer/Viewer.exe");

    private final String relativePath;

    BundledTool(String relativePath) {
        this.relativePath = relativePath;
    }

    public Path path() {
        return AppPaths.resolve(relativePath);
    }
}
