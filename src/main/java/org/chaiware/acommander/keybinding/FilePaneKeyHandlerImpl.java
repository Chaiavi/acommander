package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import java.io.File;
import java.util.Map;

import static javafx.scene.input.KeyCode.*;


public class FilePaneKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public FilePaneKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        Map<KeyCombination, Runnable> comboActions = Map.of(
                SHIFT_F8, commander::deleteFile
        );
        for (var entry : comboActions.entrySet()) {
            if (entry.getKey().match(event)) {
                entry.getValue().run();
                return true;
            }
        }

        if (event.isAltDown() || event.isShiftDown() || event.isControlDown()) {
            return false;
        }

        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.updateBottomButtons(event.getCode());
            return false;
        }

        return switch (event.getCode()) {
            case F2 -> {
                commander.renameFile();
                yield true;
            }
            case F3 -> {
                commander.viewFile();
                yield true;
            }
            case F4 -> {
                commander.editFile();
                yield true;
            }
            case F5 -> {
                commander.copyFile();
                yield true;
            }
            case F6 -> {
                commander.moveFile();
                yield true;
            }
            case F7 -> {
                commander.makeDirectory();
                yield true;
            }
            case F8, DELETE -> {
                commander.deleteFile();
                yield true;
            }
            case F11 -> {
                commander.pack();
                yield true;
            }
            case F12 -> {
                commander.unpackFile();
                yield true;
            }
            case BACK_SPACE -> {
                goUpOneFolder();
                yield true;
            }
            case ENTER -> {
                commander.enterSelectedItem();
                event.consume();
                yield true;
            }
            default -> {
                String keyText = event.getText();
                if (keyText.matches("[a-zA-Z0-9]")) {
                    commander.filterByChar(keyText.charAt(0));
                    yield true;
                }
                yield false;
            }
        };
    }

    private void goUpOneFolder() {
        File parent = new File(commander.filesPanesHelper.getFocusedPath()).getParentFile();
        if (parent != null)
            commander.filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
    }
}
