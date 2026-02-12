package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

public class CommandPaletteKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;

    public CommandPaletteKeyHandlerImpl(Commander commander) {
        this.commander = commander;
    }

    @Override
    public boolean handle(KeyEvent event) {
        if (!commander.isCommandPaletteOpen()) {
            return false;
        }
        switch (event.getCode()) {
            case ESCAPE -> {
                commander.closeCommandPalette();
                event.consume();
            }
            case ENTER -> {
                commander.executeCommandPaletteSelection();
                event.consume();
            }
            case DOWN -> {
                commander.selectNextCommandPaletteAction();
                event.consume();
            }
            case UP -> {
                commander.selectPreviousCommandPaletteAction();
                event.consume();
            }
            default -> {
            }
        }
        return true;
    }
}
