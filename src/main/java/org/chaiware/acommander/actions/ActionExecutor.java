package org.chaiware.acommander.actions;

import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.PromptDefinition;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);
    private final Commander commander;

    public ActionExecutor(Commander commander) {
        this.commander = commander;
    }

    public void execute(ActionDefinition action) {
        if (action == null) {
            return;
        }
        logger.info("Executing action: {} (Type: {})", action.getId(), action.getType());

        if (commander.filesPanesHelper.getFocusedFileSystem() instanceof FtpFileSystem && !action.isFtp()) {
            commander.showError(action.getLabel(), "The '" + action.getLabel() + "' action is not supported on FTP folders.");
            return;
        }

        if (writesToReadOnlyPane(action.getWrites())) {
            commander.showReadOnlyLocationWarning();
            return;
        }

        String blocked = SelectionRule.fromString(action.getSelection())
                .blockedMessage(action.getLabel(), commander.filesPanesHelper.getSelectedItems());
        if (blocked != null) {
            logger.info("Blocked action {}: {}", action.getId(), blocked);
            commander.showToast(blocked);
            return;
        }

        String type = action.getType();
        if ("external".equalsIgnoreCase(type)) {
            executeExternal(action);
        } else {
            executeBuiltin(action);
        }
    }

    private boolean writesToReadOnlyPane(ActionDefinition.WriteTarget writes) {
        return switch (writes) {
            case NONE -> false;
            case SOURCE -> commander.filesPanesHelper.getFocusedFileSystem().isReadOnly();
            case TARGET -> commander.filesPanesHelper.getUnfocusedFileSystem().isReadOnly();
            case BOTH -> commander.filesPanesHelper.getFocusedFileSystem().isReadOnly()
                    || commander.filesPanesHelper.getUnfocusedFileSystem().isReadOnly();
        };
    }

    private void executeBuiltin(ActionDefinition action) {
        String id = action.getBuiltin() == null ? action.getId() : action.getBuiltin();
        BuiltinAction.fromId(id).ifPresentOrElse(
                builtin -> handler(builtin).run(),
                () -> logger.warn("Unknown builtin action id: {}", id));
    }

    /** Exhaustive on purpose: a new {@link BuiltinAction} without a handler does not compile. */
    private Runnable handler(BuiltinAction builtin) {
        return switch (builtin) {
            case HELP -> commander::help;
            case ABOUT -> commander::about;
            case SETTINGS -> commander::openSettings;
            case RENAME -> commander::renameFile;
            case VIEW -> commander::viewFile;
            case EDIT -> commander::editFile;
            case COPY -> commander::copyFile;
            case DUPLICATE -> commander::duplicateFile;
            case MOVE -> commander::moveFile;
            case MKDIR -> commander::makeDirectory;
            case MKFILE -> commander::makeFile;
            case DELETE -> commander::deleteFile;
            case DELETE_WIPE -> commander::deleteWipe;
            case TERMINAL -> commander::terminalHere;
            case EXPLORER -> commander::explorerHere;
            case SEARCH -> commander::search;
            case FIND_IN_FILES -> commander::findInFiles;
            case PACK -> commander::pack;
            case SPLIT_LARGE_FILE -> commander::splitLargeFile;
            case CONVERT_MEDIA_FILE -> commander::convertMediaFile;
            case CONVERT_GRAPHICS_FILES -> commander::convertGraphicsFiles;
            case CONVERT_AUDIO_FILES -> commander::convertAudioFiles;
            case CHECKSUM_FILE -> commander::checksumFile;
            case CHECKSUM_FOLDER_CONTENTS -> commander::checksumFolderContents;
            case ANALYZE_FILE -> commander::analyzeFile;
            case UNPACK -> commander::unpackFile;
            case EXTRACT_ALL -> commander::extractAll;
            case MERGE_PDF -> commander::mergePDFFiles;
            case EXTRACT_PDF_PAGES -> commander::extractPDFPages;
            case COMPARE_FILES -> commander::compareFiles;
            case COMPARE_FOLDERS -> commander::compareFolders;
            case CHANGE_ATTRIBUTES -> commander::changeAttributes;
            case FILE_PROPERTIES -> commander::fileProperties;
            case EDIT_IMAGE_METADATA -> commander::editImageMetadata;
            case REMOVE_IMAGE_METADATA -> commander::removeImageMetadata;
            case EDIT_VIDEO_METADATA -> commander::editVideoMetadata;
            case REMOVE_VIDEO_METADATA -> commander::removeVideoMetadata;
            case EDIT_AUDIO_METADATA -> commander::editAudioMetadata;
            case REMOVE_AUDIO_METADATA -> commander::removeAudioMetadata;
            case COMPRESS_EXECUTABLE -> commander::compressExecutable;
            case REFRESH -> commander::refreshPanesAndDrives;
            case OPEN_COMMAND_PALETTE -> commander::openCommandPalette;
            case LEFT_PATH_COMBO -> () -> commander.leftPathComboBox.show();
            case RIGHT_PATH_COMBO -> () -> commander.rightPathComboBox.show();
            case SYNC_TO_OTHER_PANE -> commander::syncToOtherPane;
            case TOGGLE_DARK_MODE -> commander::toggleDarkMode;
            case SORT_BY_NAME -> commander::sortByName;
            case SORT_BY_SIZE -> commander::sortBySize;
            case SORT_BY_DATE -> commander::sortByDate;
            case BOOKMARK_THIS_PATH -> commander::bookmarkCurrentPath;
            case GOTO_BOOKMARK -> commander::gotoBookmark;
            case REMOVE_BOOKMARK -> commander::removeBookmark;
            case FTP_CONNECT -> commander::ftpConnect;
            case FTP_DISCONNECT -> commander::ftpDisconnect;
            case OPEN_HOSTS_FILE -> commander::openHostsFile;
            case SELECT_ALL -> commander::selectAll;
            case UNSELECT_ALL -> commander::unselectAll;
            case INVERT_SELECTION -> commander::invertSelection;
            case SELECT_BY_PATTERN -> commander::selectByPattern;
            case COPY_SELECTION -> commander::copySelectionToClipboard;
            case CUT_SELECTION -> commander::cutSelectionToClipboard;
            case PASTE_SELECTION -> commander::pasteClipboardSelection;
            case REPORT_BUG -> commander::reportBug;
        };
    }

    private void executeExternal(ActionDefinition action) {
        if (action.getPath() == null || action.getPath().isBlank()) {
            logger.warn("Missing path for external action: {}", action.getId());
            return;
        }
        java.util.Map<String, String> extraValues = new java.util.HashMap<>();
        PromptDefinition prompt = action.getPrompt();
        if (prompt != null) {
            String defaultValue = ToolCommandBuilder.resolveTemplate(
                    prompt.getDefaultValue(),
                    commander.filesPanesHelper,
                    java.util.Map.of(),
                    null
            );
            java.util.Optional<String> result = commander.promptUser(
                    defaultValue == null ? "" : defaultValue,
                    prompt.getTitle(),
                    prompt.getLabel()
            );
            if (result.isEmpty()) {
                return;
            }
            extraValues.put("${promptValue}", result.get());
        }

        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                commander.filesPanesHelper,
                extraValues,
                null
        );
        if (command.isEmpty()) {
            logger.warn("No command generated for external action: {}", action.getId());
            return;
        }
        boolean refresh = action.getRefreshAfter() != null
                ? action.getRefreshAfter()
                : false;
        commander.runExternalReported(command, refresh, action.getLabel());
    }
}
