package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import java.io.File;


public class FilePaneKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public FilePaneKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        if (event.isAltDown() || event.isShiftDown() || event.isControlDown()) {
            return false;
        }

        switch (event.getCode()) {
            case F2 -> {
                commander.renameFile();
                return true;
            }
            case F3 -> {
                commander.viewFile();
                return true;
            }
            case F4 -> {
                commander.editFile();
                return true;
            }
            case F5 -> {
                commander.copyFile();
                return true;
            }
            case F6 -> {
                commander.moveFile();
                return true;
            }
            case F7 -> {
                commander.makeDirectory();
                return true;
            }
            case F8, DELETE -> {
                commander.deleteFile();
                return true;
            }
            case F11 -> {
                commander.pack();
                return true;
            }
            case F12 -> {
                commander.unpackFile();
                return true;
            }
            case BACK_SPACE -> {
                goUpOneFolder();
                return true;
            }
            case ENTER -> {
                commander.enterSelectedItem();
                event.consume();
                return true;
            }
            default -> {
                String keyText = event.getText();
                if (keyText.matches("[a-zA-Z0-9]")) {
                    commander.filterByChar(keyText.charAt(0));
                    return true;
                }
                return false;
            }
        }
    }

    private void goUpOneFolder() {
        File parent = new File(commander.filesPanesHelper.getFocusedPath()).getParentFile();
        if (parent != null)
            commander.filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
    }
}
