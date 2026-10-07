package org.chaiware.acommander.actions;

import java.util.Arrays;
import java.util.Optional;

/** Builtin action ids used in apps.json ({@code builtin}, or {@code id} when absent). */
public enum BuiltinAction {
    HELP("help"),
    ABOUT("about"),
    SETTINGS("settings"),
    RENAME("rename"),
    VIEW("view"),
    EDIT("edit"),
    COPY("copy"),
    DUPLICATE("duplicate"),
    MOVE("move"),
    MKDIR("mkdir"),
    MKFILE("mkfile"),
    DELETE("delete"),
    DELETE_WIPE("deleteWipe"),
    TERMINAL("terminal"),
    EXPLORER("explorer"),
    SEARCH("search"),
    FIND_IN_FILES("findInFiles"),
    PACK("pack"),
    SPLIT_LARGE_FILE("splitLargeFile"),
    CONVERT_MEDIA_FILE("convertMediaFile"),
    CONVERT_GRAPHICS_FILES("convertGraphicsFiles"),
    CONVERT_AUDIO_FILES("convertAudioFiles"),
    CONVERT_VIDEO_FILES("convertVideoFiles"),
    TRIM_MEDIA("trimMedia"),
    JOIN_MEDIA("joinMedia"),
    MEDIA_INFO("mediaInfo"),
    CHECKSUM_FILE("checksumFile"),
    CHECKSUM_FOLDER_CONTENTS("checksumFolderContents"),
    ANALYZE_FILE("analyzeFile"),
    UNPACK("unpack"),
    EXTRACT_ALL("extractAll"),
    MERGE_PDF("mergePdf"),
    EXTRACT_PDF_PAGES("extractPdfPages"),
    COMPARE_FILES("compareFiles"),
    COMPARE_FOLDERS("compareFolders"),
    CHANGE_ATTRIBUTES("changeAttributes"),
    FILE_PROPERTIES("fileProperties"),
    EDIT_IMAGE_METADATA("editImageMetadata"),
    REMOVE_IMAGE_METADATA("removeImageMetadata"),
    EDIT_VIDEO_METADATA("editVideoMetadata"),
    REMOVE_VIDEO_METADATA("removeVideoMetadata"),
    EDIT_AUDIO_METADATA("editAudioMetadata"),
    REMOVE_AUDIO_METADATA("removeAudioMetadata"),
    COMPRESS_EXECUTABLE("compressExecutable"),
    REFRESH("refresh"),
    OPEN_COMMAND_PALETTE("openCommandPalette"),
    LEFT_PATH_COMBO("leftPathCombo"),
    RIGHT_PATH_COMBO("rightPathCombo"),
    SYNC_TO_OTHER_PANE("syncToOtherPane"),
    LINK_NAVIGATION("linkNavigation"),
    UNLINK_NAVIGATION("unlinkNavigation"),
    TOGGLE_DARK_MODE("toggleDarkMode"),
    SORT_BY_NAME("sortByName"),
    SORT_BY_SIZE("sortBySize"),
    SORT_BY_DATE("sortByDate"),
    BOOKMARK_THIS_PATH("bookmarkThisPath"),
    GOTO_BOOKMARK("gotoBookmark"),
    REMOVE_BOOKMARK("removeBookmark"),
    FTP_CONNECT("ftpConnect"),
    FTP_DISCONNECT("ftpDisconnect"),
    OPEN_HOSTS_FILE("openHostsFile"),
    SELECT_ALL("selectAll"),
    UNSELECT_ALL("unselectAll"),
    INVERT_SELECTION("invertSelection"),
    SELECT_BY_PATTERN("selectByPattern"),
    COPY_SELECTION("copySelection"),
    CUT_SELECTION("cutSelection"),
    PASTE_SELECTION("pasteSelection"),
    REPORT_BUG("reportBug");

    private final String id;

    BuiltinAction(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<BuiltinAction> fromId(String id) {
        return Arrays.stream(values()).filter(builtin -> builtin.id.equals(id)).findFirst();
    }
}
