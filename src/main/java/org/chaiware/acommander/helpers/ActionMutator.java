package org.chaiware.acommander.helpers;

import java.util.Set;

/**
 * Helper class for determining if an action modifies files or is read-only.
 * Used to block write operations on read-only archives.
 */
public class ActionMutator {

    /**
     * Set of action IDs that ALWAYS modify files (blocked in read-only archives).
     * These actions are blocked when the focused pane is viewing a read-only archive.
     */
    private static final Set<String> ALWAYS_WRITE_ACTIONS = Set.of(
        // File/folder creation and deletion
        "mkdir",
        "mkfile",
        "delete",
        "deleteWipe",
        "unlockDelete",
        
        // Attributes
        "changeAttributes"
    );

    /**
     * Set of action IDs that modify files CONDITIONALLY.
     * These are blocked only when the TARGET is in a read-only archive.
     * For these actions, additional context checking is needed.
     */
    private static final Set<String> CONDITIONAL_WRITE_ACTIONS = Set.of(
        // File operations - write only when target is in read-only archive
        "rename",      // modifies in same folder
        "multiRename", // modifies in same folder
        "copy",        // modifies target folder
        "move",        // modifies target folder
        "edit",        // might modify file
        
        // Archive creation/modification (usually creates new files in target folder)
        "pack",
        "splitLargeFile",
        
        // Unpacking (writes to target folder)
        "unpack",
        "extractAll",
        
        // PDF operations (create new files in target folder)
        "mergePdf",
        "extractPdfPages",
        
        // Convert operations (create new files in target folder)
        "convertMediaFile",
        "convertGraphicsFiles",
        "convertAudioFiles"
    );

    /**
     * Set of action IDs that are ALWAYS read-only.
     * These actions are always allowed even in read-only archives.
     */
    private static final Set<String> READ_ONLY_ACTIONS = Set.of(
        "view",
        "search",
        "findInFiles",
        "checksumFile",
        "checksumFolderContents",
        "compareFiles",
        "compareFolders",
        "explorer",
        "terminal"
    );

    /**
     * Checks if an action is an always-write action.
     * @param actionId The action ID to check
     * @return true if the action always modifies files
     */
    public static boolean isAlwaysWriteAction(String actionId) {
        return ALWAYS_WRITE_ACTIONS.contains(actionId);
    }

    /**
     * Checks if an action is a conditional write action.
     * @param actionId The action ID to check
     * @return true if the action may modify files depending on context
     */
    public static boolean isConditionalWriteAction(String actionId) {
        return CONDITIONAL_WRITE_ACTIONS.contains(actionId);
    }

    /**
     * Checks if an action is read-only.
     * @param actionId The action ID to check
     * @return true if the action is read-only
     */
    public static boolean isReadOnlyAction(String actionId) {
        return READ_ONLY_ACTIONS.contains(actionId);
    }

    /**
     * Checks if an action can be performed on a read-only archive.
     * For simple cases, checks if action is not a write action.
     * For conditional cases (copy, move, rename), additional context is needed.
     * 
     * @param actionId The action ID to check
     * @return true if the action is allowed on read-only archives
     */
    public static boolean isAllowedOnReadOnlyArchive(String actionId) {
        // Read-only actions are always allowed
        if (isReadOnlyAction(actionId)) {
            return true;
        }
        // Always-write actions are never allowed
        if (isAlwaysWriteAction(actionId)) {
            return false;
        }
        // Conditional actions need context - default to allowing them
        // (the caller should do additional checking)
        return true;
    }
}
