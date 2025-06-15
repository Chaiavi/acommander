package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;


public interface IKeyHandler {
    KeyCombination ALT_F1_COMBO = new KeyCodeCombination(KeyCode.F1, KeyCombination.ALT_DOWN);
    KeyCombination ALT_F2_COMBO = new KeyCodeCombination(KeyCode.F2, KeyCombination.ALT_DOWN);

    boolean handle(KeyEvent event);
}
