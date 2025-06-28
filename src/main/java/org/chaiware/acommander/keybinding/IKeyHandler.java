package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.scene.input.KeyCode.*;
import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;


public interface IKeyHandler {
    Logger logger = LoggerFactory.getLogger(IKeyHandler.class);

    KeyCombination ALT_F1 = new KeyCodeCombination(F1, ALT_DOWN);
    KeyCombination ALT_F2 = new KeyCodeCombination(F2, ALT_DOWN);
    KeyCombination ALT_F3 = new KeyCodeCombination(F3, ALT_DOWN);
    KeyCombination ALT_F4 = new KeyCodeCombination(F4, ALT_DOWN);
    KeyCombination ALT_F5 = new KeyCodeCombination(F5, ALT_DOWN);
    KeyCombination ALT_F6 = new KeyCodeCombination(F6, ALT_DOWN);
    KeyCombination ALT_F7 = new KeyCodeCombination(F7, ALT_DOWN);
    KeyCombination ALT_F8 = new KeyCodeCombination(F8, ALT_DOWN);
    KeyCombination ALT_F9 = new KeyCodeCombination(F9, ALT_DOWN);
    KeyCombination ALT_F10 = new KeyCodeCombination(F10, ALT_DOWN);
    KeyCombination ALT_F11 = new KeyCodeCombination(F11, ALT_DOWN);
    KeyCombination ALT_F12 = new KeyCodeCombination(F12, ALT_DOWN);

    KeyCombination SHIFT_F1 = new KeyCodeCombination(F1, SHIFT_DOWN);
    KeyCombination SHIFT_F2 = new KeyCodeCombination(F2, SHIFT_DOWN);
    KeyCombination SHIFT_F3 = new KeyCodeCombination(F3, SHIFT_DOWN);
    KeyCombination SHIFT_F4 = new KeyCodeCombination(F4, SHIFT_DOWN);
    KeyCombination SHIFT_F5 = new KeyCodeCombination(F5, SHIFT_DOWN);
    KeyCombination SHIFT_F6 = new KeyCodeCombination(F6, SHIFT_DOWN);
    KeyCombination SHIFT_F7 = new KeyCodeCombination(F7, SHIFT_DOWN);
    KeyCombination SHIFT_F8 = new KeyCodeCombination(F8, SHIFT_DOWN);
    KeyCombination SHIFT_F9 = new KeyCodeCombination(F9, SHIFT_DOWN);
    KeyCombination SHIFT_F10 = new KeyCodeCombination(F10, SHIFT_DOWN);
    KeyCombination SHIFT_F11 = new KeyCodeCombination(F11, SHIFT_DOWN);
    KeyCombination SHIFT_F12 = new KeyCodeCombination(F12, SHIFT_DOWN);

    boolean handle(KeyEvent event);
}
