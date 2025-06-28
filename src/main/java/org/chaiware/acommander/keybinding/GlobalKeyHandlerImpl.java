package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import java.util.Map;

import static javafx.scene.input.KeyCode.ALT;
import static javafx.scene.input.KeyCode.SHIFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

public class GlobalKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public GlobalKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        logger.trace("Key event phase: {}", event.getEventType());
        logger.trace("Event target: {}", event.getTarget());
        logger.trace("Event source: {}", event.getSource());

        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT) {
            commander.updateBottomButtons(event.getCode());
            return false;
        }

        Map<KeyCombination, Runnable> comboActions = Map.of(
                ALT_F1, () -> commander.leftPathComboBox.show(),
                ALT_F2, () -> commander.rightPathComboBox.show(),
                ALT_F7, commander::makeFile,
                ALT_F9, commander::explorerHere,
                ALT_F12, commander::extractAll,
                SHIFT_F1, commander::extractPDFPages,
                SHIFT_F2, commander::mergePDFFiles
        );

        for (var entry : comboActions.entrySet()) {
            if (entry.getKey().match(event)) {
                entry.getValue().run();
                return true;
            }
        }

        return switch (event.getCode()) {
            case F1 -> { commander.help(); yield true; }
            case F9 -> { commander.terminalHere(); yield true; }
            case F10 -> { commander.search(); event.consume(); yield true; }
            case TAB -> { clickTab(); event.consume(); yield true; }
            default -> false;
        };
    }


    public void handleKeyReleased(KeyEvent event) {
        commander.updateBottomButtons(null);
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
