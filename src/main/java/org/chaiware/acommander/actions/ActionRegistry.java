package org.chaiware.acommander.actions;

import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.*;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.FtpFileSystem;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ActionRegistry {
    private final List<AppAction> actions;
    private final ActionPriorityEngine priorityEngine = new ActionPriorityEngine();

    public ActionRegistry(AppRegistry appRegistry, ActionExecutor executor) {
        actions = appRegistry.actionsForScope(ActionScope.COMMAND_PALETTE).stream()
                .map(action -> toAppAction(action, executor))
                .collect(Collectors.toList());
    }

    private AppAction toAppAction(ActionDefinition action, ActionExecutor executor) {
        SelectionRule rule = SelectionRule.fromString(action.getSelection());

        // Special handling for fileProperties to show dynamic label (File/Folder Properties)
        if ("fileProperties".equals(action.getId())) {
            return new AppAction(
                    action.getId(),
                    action.getLabel(),
                    this::getFilePropertiesDynamicLabel,
                    action.getShortcut(),
                    action.getAliases(),
                    ctx -> priorityEngine.priority(action, ctx),
                    ctx -> rule.isSatisfied(selectedItemsOrEmpty(ctx))
                            && isSelectionAllowed(action, ctx),
                    ctx -> executor.execute(action)
            );
        }

        // Special handling for duplicate to show dynamic label (Duplicate File/s or Duplicate Folder/s)
        if ("duplicate".equals(action.getId())) {
            return new AppAction(
                    action.getId(),
                    action.getLabel(),
                    this::getDuplicateDynamicLabel,
                    action.getShortcut(),
                    action.getAliases(),
                    ctx -> priorityEngine.priority(action, ctx),
                    ctx -> rule.isSatisfied(selectedItemsOrEmpty(ctx))
                            && isSelectionAllowed(action, ctx),
                    ctx -> executor.execute(action)
            );
        }

        return new AppAction(
                action.getId(),
                action.getLabel(),
                action.getShortcut(),
                action.getAliases(),
                ctx -> priorityEngine.priority(action, ctx),
                ctx -> rule.isSatisfied(selectedItemsOrEmpty(ctx))
                        && isSelectionAllowed(action, ctx),
                ctx -> executor.execute(action)
        );
    }

    private String getFilePropertiesDynamicLabel(ActionContext ctx) {
        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return null;
        }
        List<FileItem> selected = ctx.commander().filesPanesHelper.getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        FileItem item = selected.getFirst();
        if (item.isDirectory()) {
            return "Folder Properties";
        }
        return "File Properties";
    }

    private String getDuplicateDynamicLabel(ActionContext ctx) {
        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return null;
        }
        List<FileItem> selected = ctx.commander().filesPanesHelper.getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        
        // Check if all selected items are folders
        boolean allFolders = selected.stream().allMatch(FileItem::isDirectory);
        // Check if all selected items are files
        boolean allFiles = selected.stream().allMatch(item -> !item.isDirectory());
        
        if (allFolders) {
            return selected.size() == 1 ? "Duplicate Folder" : "Duplicate Folders";
        } else if (allFiles) {
            return selected.size() == 1 ? "Duplicate File" : "Duplicate Files";
        } else {
            // Mixed selection
            return "Duplicate Files and Folders";
        }
    }

    private List<FileItem> selectedItemsOrEmpty(ActionContext ctx) {
        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return Collections.emptyList();
        }
        List<FileItem> selectedItems = ctx.commander().filesPanesHelper.getSelectedItems();
        return selectedItems == null ? Collections.emptyList() : selectedItems;
    }

    private boolean isSelectionAllowed(ActionDefinition action, ActionContext ctx) {
        if (ctx == null || ctx.commander() == null) {
            return action.getRequires().isEmpty() && action.getFileTypes().isEmpty();
        }
        FilesPanesHelper panes = ctx.commander().filesPanesHelper;
        if (panes != null && panes.getFocusedFileSystem() instanceof FtpFileSystem && !action.isFtp()) {
            return false;
        }
        if (!action.getRequires().stream().allMatch(requirement -> isMet(requirement, ctx.commander()))) {
            return false;
        }
        if (action.getFileTypes().isEmpty()) {
            return true;
        }
        List<FileItem> selected = selectedItemsOrEmpty(ctx);
        return action.getFileTypes().stream().anyMatch(type -> areAllOfType(type, selected));
    }

    private static boolean isMet(ActionDefinition.Requirement requirement, Commander commander) {
        FilesPanesHelper panes = commander.filesPanesHelper;
        return switch (requirement) {
            case CLIPBOARD_HAS_FILES -> commander.hasClipboardTransferEntries();
            case FOCUSED_PANE_IS_FTP -> panes != null && panes.getFileSystem(panes.getFocusedSide()) instanceof FtpFileSystem;
            case TEXT_FILE_IN_EACH_PANE -> panes != null && commander.canCompareSelectedFiles();
            case NAVIGATION_LINKED -> commander.isNavigationLinked();
            case NAVIGATION_UNLINKED -> !commander.isNavigationLinked();
        };
    }

    static boolean areAllOfType(ActionDefinition.FileType type, List<FileItem> items) {
        return switch (type) {
            case CONVERTIBLE_IMAGE -> ImageConversionSupport.areAllConvertibleImages(items);
            case CONVERTIBLE_AUDIO -> AudioConversionSupport.areAllConvertibleAudio(items);
            case IMAGE_WITH_METADATA -> ImageMetadataSupport.areAllSupportedImages(items);
            case VIDEO_WITH_METADATA -> VideoMetadataSupport.areAllSupportedVideos(items);
            case AUDIO_WITH_METADATA -> AudioMetadataSupport.areAllSupportedAudio(items);
            case EXECUTABLE -> ExecutableCompressionSupport.areAllSupportedExecutables(items);
            case ARCHIVE -> FileItem.allFilesWithExtension(items, ArchiveMode::isUnpackable);
            case PDF -> FileItem.allFilesWithExtension(items, "pdf"::equals);
        };
    }

    public List<AppAction> all() {
        return actions;
    }
}
