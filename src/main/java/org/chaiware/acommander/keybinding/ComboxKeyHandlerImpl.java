package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.model.Folder;

public class ComboxKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public ComboxKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        return switch (event.getCode()) {
            case ENTER -> {
                commander.leftPathComboBox.setValue(new Folder(commander.leftPathComboBox.getEditor().getText()));
                commander.rightPathComboBox.setValue(new Folder(commander.rightPathComboBox.getEditor().getText()));
                commander.filesPanesHelper.refreshFileListViews();
                yield true;
            }
            default -> false;
        };
    }
}
