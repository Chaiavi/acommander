package org.chaiware.acommander.config;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.junit.jupiter.api.Test;

class ActionScopeTest {

    @Test
    void fromIdIsCaseInsensitiveAndReturnsEmptyForUnknown() {
        Assertions.assertThat(ActionScope.fromId("GLOBAL"))
                .contains(ActionScope.GLOBAL);
        Assertions.assertThat(ActionScope.fromId("filePane"))
                .contains(ActionScope.FILE_PANE);
        Assertions.assertThat(ActionScope.fromId("missing"))
                .isEmpty();
        Assertions.assertThat(ActionScope.fromId(null))
                .isEmpty();
    }

    @Test
    void fromKeyContextMapsToMatchingScope() {
        Assertions.assertThat(ActionScope.fromKeyContext(KeyBindingManager.KeyContext.FILE_PANE))
                .isEqualTo(ActionScope.FILE_PANE);
        Assertions.assertThat(ActionScope.fromKeyContext(KeyBindingManager.KeyContext.PATH_COMBO_BOX))
                .isEqualTo(ActionScope.PATH_COMBO);
        Assertions.assertThat(ActionScope.fromKeyContext(KeyBindingManager.KeyContext.COMMAND_PALETTE))
                .isEqualTo(ActionScope.COMMAND_PALETTE);
        Assertions.assertThat(ActionScope.fromKeyContext(KeyBindingManager.KeyContext.DIALOG))
                .isEqualTo(ActionScope.DIALOG);
        Assertions.assertThat(ActionScope.fromKeyContext(KeyBindingManager.KeyContext.GLOBAL))
                .isEqualTo(ActionScope.GLOBAL);
    }
}
