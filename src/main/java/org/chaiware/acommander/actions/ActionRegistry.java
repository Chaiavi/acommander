package org.chaiware.acommander.actions;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AudioConversionSupport;
import org.chaiware.acommander.helpers.ImageConversionSupport;
import org.chaiware.acommander.model.FileItem;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ActionRegistry {
    private final List<AppAction> actions;

    public ActionRegistry(AppRegistry appRegistry, ActionExecutor executor) {
        actions = appRegistry.actionsForScope(ActionScope.COMMAND_PALETTE).stream()
                .map(action -> toAppAction(action, executor))
                .collect(Collectors.toList());
    }

    private AppAction toAppAction(ActionDefinition action, ActionExecutor executor) {
        SelectionRule rule = SelectionRule.fromString(action.getSelection());
        String builtin = action.getBuiltin() == null ? action.getId() : action.getBuiltin();
        return new AppAction(
                action.getId(),
                action.getLabel(),
                action.getShortcut(),
                action.getAliases(),
                ctx -> rule.isSatisfied(selectedItemsOrEmpty(ctx))
                        && isSelectionAllowedForBuiltin(builtin, ctx),
                ctx -> executor.execute(action)
        );
    }

    private List<FileItem> selectedItemsOrEmpty(ActionContext ctx) {
        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return Collections.emptyList();
        }
        List<FileItem> selectedItems = ctx.commander().filesPanesHelper.getSelectedItems();
        return selectedItems == null ? Collections.emptyList() : selectedItems;
    }

    private boolean isSelectionAllowedForBuiltin(String builtin, ActionContext ctx) {
        if (ctx != null && ctx.commander() != null && ctx.commander().filesPanesHelper != null) {
            var fs = ctx.commander().filesPanesHelper.getFocusedFileSystem();
            if (fs instanceof org.chaiware.acommander.vfs.FtpFileSystem) {
                // List of supported FTP actions
                boolean supported = switch (builtin) {
                    case "help", "settings", "rename", "view", "edit", "copy", "move", "mkdir", "mkfile", 
                         "delete", "refresh", "openCommandPalette", "leftPathCombo", 
                         "rightPathCombo", "setDarkMode", "setLightMode", 
                         "setRegularMode", "toggleDarkMode", "sortByName", "sortBySize", "sortByDate", 
                         "gotoBookmark", "removeBookmark", "ftpConnect", "ftpDisconnect" -> true;
                    default -> false;
                };
                if (!supported) return false;
            }
        }

        // Additional checks for specific actions regardless of FS
        if ("ftpDisconnect".equals(builtin)) {
            if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
                return false;
            }
            var filesPanesHelper = ctx.commander().filesPanesHelper;
            var currentFs = filesPanesHelper.getFileSystem(filesPanesHelper.getFocusedSide());
            return currentFs instanceof org.chaiware.acommander.vfs.FtpFileSystem;
        }

        if (!"convertGraphicsFiles".equals(builtin)
                && !"convertAudioFiles".equals(builtin)
                && !"convertMediaFile".equals(builtin)
                && !"compareFiles".equals(builtin)
                && !"unpack".equals(builtin)
                && !"extractAll".equals(builtin)
                && !"extractPdfPages".equals(builtin)
                && !"mergePdf".equals(builtin)) {
            return true;
        }

        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return false;
        }

        List<FileItem> selectedItems = ctx.commander().filesPanesHelper.getSelectedItems();

        if ("compareFiles".equals(builtin)) {
            return ctx.commander().canCompareSelectedFiles();
        }
        if ("unpack".equals(builtin) || "extractAll".equals(builtin)) {
            return selectedItems != null && !selectedItems.isEmpty() && selectedItems.stream().allMatch(item ->
                    !item.isDirectory() && org.chaiware.acommander.helpers.ArchiveService.isSupportedArchiveExtension(
                            item.getName().contains(".") ? item.getName().substring(item.getName().lastIndexOf('.') + 1) : ""
                    )
            );
        }
        if ("extractPdfPages".equals(builtin)) {
            return selectedItems != null && !selectedItems.isEmpty() && selectedItems.stream().allMatch(item ->
                    !item.isDirectory() && item.getName().toLowerCase().endsWith(".pdf")
            );
        }
        if ("mergePdf".equals(builtin)) {
            return selectedItems != null && selectedItems.size() >= 2 && selectedItems.stream().allMatch(item ->
                    !item.isDirectory() && item.getName().toLowerCase().endsWith(".pdf")
            );
        }
        if ("convertMediaFile".equals(builtin)) {
            return ImageConversionSupport.areAllConvertibleImages(selectedItems)
                    || AudioConversionSupport.areAllConvertibleAudio(selectedItems);
        }
        if ("convertGraphicsFiles".equals(builtin)) {
            return ImageConversionSupport.areAllConvertibleImages(selectedItems);
        }
        return AudioConversionSupport.areAllConvertibleAudio(selectedItems);
    }

    public List<AppAction> all() {
        return actions;
    }
}
