package org.chaiware.acommander.services;

import java.io.File;
import java.util.Optional;

/** Linked navigation: the other pane repeats each folder step (into a subfolder, up) the focused pane takes. */
public final class LinkedNavigation {
    private LinkedNavigation() {
    }

    /** Where the pane at {@code otherPath} goes: its parent when {@code intoName} is null, else that subfolder; empty when it has none. */
    public static Optional<String> target(String otherPath, String intoName) {
        File other = new File(otherPath);
        File target = intoName == null ? other.getParentFile() : new File(other, intoName);
        return target != null && target.isDirectory() ? Optional.of(target.getAbsolutePath()) : Optional.empty();
    }
}
