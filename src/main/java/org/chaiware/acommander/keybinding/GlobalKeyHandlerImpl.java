package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

public class GlobalKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public GlobalKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        if (ALT_F1_COMBO.match(event)) { commander.leftPathComboBox.show(); return true; }
        if (ALT_F2_COMBO.match(event)) { commander.rightPathComboBox.show(); return true; }

        return switch (event.getCode()) {
            case F1 -> { commander.help(); yield true; }
            case F9 -> { commander.terminalHere(); yield true; }
            case F10 -> { commander.search(); event.consume(); yield true; }
            case TAB -> { clickTab(); event.consume(); yield true; }
            default -> false;
        };
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
