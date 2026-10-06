package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IKeyHandlerTest {

    @Test
    void syncModifiersCopiesTheEventsModifierState() {
        Set<KeyCode> active = EnumSet.of(KeyCode.ALT, KeyCode.A);
        KeyEvent shiftAndCtrl = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.B, true, true, false, false);

        IKeyHandler.syncModifiers(active, shiftAndCtrl);

        assertThat(active).containsExactlyInAnyOrder(KeyCode.SHIFT, KeyCode.CONTROL, KeyCode.A);
    }
}
