package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import static javafx.scene.input.KeyCode.ALT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

public class GlobalKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public GlobalKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        logger.trace("Key event phase: " + event.getEventType());
        logger.trace("Event target: " + event.getTarget());
        logger.trace("Event source: " + event.getSource());

        // Handle ALT for button labels
        if (event.getCode() == ALT) {
            commander.updateBottomButtons(true);
            return false;
        }

        if (ALT_F1.match(event)) {
            commander.leftPathComboBox.show();
            return true;
        }
        if (ALT_F2.match(event)) {
            commander.rightPathComboBox.show();
            return true;
        }
        if (ALT_F7.match(event)) {
            commander.makeFile();
            event.consume();
            return true;
        }

        return switch (event.getCode()) {
            case F1 -> {
                commander.help();
                yield true;
            }
            case F9 -> {
                commander.terminalHere();
                yield true;
            }
            case F10 -> {
                commander.search();
                event.consume();
                yield true;
            }
            case TAB -> {
                clickTab();
                event.consume();
                yield true;
            }
            default -> false;
        };
    }

    public void handleKeyReleased(KeyEvent event) {
        commander.updateBottomButtons(false);
    }

    /** Changes focus between file lists */
    private void clickTab() {
        if (commander.determineCurrentContext(commander.rootPane.getScene()) == KeyBindingManager.KeyContext.FILE_PANE) {
            if (commander.filesPanesHelper.getFocusedSide() == LEFT)
                commander.rightFileList.requestFocus();
            else
                commander.leftFileList.requestFocus();
        } else {
            commander.leftFileList.requestFocus();
        }
    }
}
