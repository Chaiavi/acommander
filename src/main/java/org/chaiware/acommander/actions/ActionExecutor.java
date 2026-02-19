package org.chaiware.acommander.actions;

import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.config.PromptDefinition;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);
    private final Commander commander;
    private final AppRegistry appRegistry;

    public ActionExecutor(Commander commander, AppRegistry appRegistry) {
        this.commander = commander;
        this.appRegistry = appRegistry;
    }

    public void execute(ActionDefinition action) {
        if (action == null) {
            return;
        }
        String type = action.getType();
        if ("external".equalsIgnoreCase(type)) {
            executeExternal(action);
        } else {
            executeBuiltin(action);
        }
    }

    private void executeBuiltin(ActionDefinition action) {
        String builtin = action.getBuiltin() == null ? action.getId() : action.getBuiltin();
        switch (builtin) {
            case "help" -> commander.help();
            case "settings" -> commander.openSettings();
            case "rename" -> commander.renameFile();
            case "view" -> commander.viewFile();
            case "edit" -> commander.editFile();
            case "copy" -> commander.copyFile();
            case "move" -> commander.moveFile();
            case "mkdir" -> commander.makeDirectory();
            case "mkfile" -> commander.makeFile();
            case "delete" -> commander.deleteFile();
            case "deleteWipe" -> commander.deleteWipe();
            case "terminal" -> commander.terminalHere();
            case "explorer" -> commander.explorerHere();
            case "search" -> commander.search();
            case "findInFiles" -> commander.findInFiles();
            case "pack" -> commander.pack();
            case "splitLargeFile" -> commander.splitLargeFile();
            case "unpack" -> commander.unpackFile();
            case "extractAll" -> commander.extractAll();
            case "mergePdf" -> commander.mergePDFFiles();
            case "extractPdfPages" -> commander.extractPDFPages();
            case "changeAttributes" -> commander.changeAttributes();
            case "refresh" -> commander.filesPanesHelper.refreshFileListViews();
            case "openCommandPalette" -> commander.openCommandPalette();
            case "leftPathCombo" -> commander.leftPathComboBox.show();
            case "rightPathCombo" -> commander.rightPathComboBox.show();
            case "syncOtherPane" -> commander.syncToOtherPane();
            case "setDarkMode" -> commander.setDarkMode();
            case "setLightMode" -> commander.setLightMode();
            case "setRegularMode" -> commander.setRegularMode();
            case "toggleDarkMode" -> commander.toggleDarkMode();
            case "sortByName" -> commander.sortByName();
            case "sortBySize" -> commander.sortBySize();
            case "sortByDate" -> commander.sortByDate();
            case "bookmarkThisPath" -> commander.bookmarkCurrentPath();
            case "gotoBookmark" -> commander.gotoBookmark();
            case "removeBookmark" -> commander.removeBookmark();
            default -> logger.warn("Unknown builtin action id: {}", builtin);
        }
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
        commander.runExternal(command, refresh);
    }
}
