package org.chaiware.acommander.services;

import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;

import java.awt.Desktop;
import java.util.List;

/** The question asked before a delete or a wipe, and whether a delete can go to the Recycle Bin. */
public final class DeleteConfirmation {
    private DeleteConfirmation() {
    }

    /**
     * ponytail: only a UNC path counts as having no Recycle Bin; on a removable or mapped network drive Windows
     * deletes for good anyway. Upgrade path: ask the shell (SHQueryRecycleBin) for the drive.
     */
    public static boolean recycles(VFileSystem fs, String folder) {
        return fs instanceof LocalFileSystem && folder != null && !folder.startsWith("\\\\")
                && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    public static String deleteQuestion(List<String> names, boolean recycle) {
        return recycle
                ? "Move " + what(names) + " to the Recycle Bin?"
                : "Permanently delete " + what(names) + "? This can't be undone.";
    }

    public static String wipeQuestion(List<String> names) {
        return "Wipe " + what(names) + "? " + (names.size() == 1 ? "It is" : "They are")
                + " overwritten and can't be recovered.";
    }

    private static String what(List<String> names) {
        return names.size() == 1 ? "\"" + names.getFirst() + "\"" : names.size() + " items";
    }
}
