package org.chaiware.acommander.tools;

import org.chaiware.acommander.helpers.AppPaths;

import java.nio.file.Path;

/** Tools under apps/ that the code runs directly. Tools run by an apps.json action are listed there instead. */
public enum BundledTool {
    SEVEN_ZIP("apps/extract_all/UniExtract/bin/x64/7z.exe"),
    CURL("apps/remote_connectivity/curl.exe"),
    EXIV2("apps/image_metadata/exiv2.exe"),
    FFMPEG("apps/media/ffmpeg.exe"),
    RIPGREP("apps/search_in_files/rg.exe"),
    CAESIUM("apps/image_convert/caesiumclt.exe"),
    FILE("apps/file_analysis/file.exe"),
    FILE_MAGIC("apps/file_analysis/magic.mgc"),
    RHASH("apps/checksum/rhash.exe"),
    EXAM_DIFF("apps/file_compare/ExamDiff.exe"),
    UPX("apps/exe_compress/upx.exe"),
    VIEWER("apps/view/UniversalViewer/Viewer.exe"),
    /** SHA-256 of every shipped file under apps/, written by the build's toolHashes task. */
    TOOL_HASHES("apps/tools.sha256");

    private final String relativePath;

    BundledTool(String relativePath) {
        this.relativePath = relativePath;
    }

    public Path path() {
        return AppPaths.resolve(relativePath);
    }

    /** The path under the app root with forward slashes, as on GitHub. */
    public String relativePath() {
        return relativePath;
    }
}
