package org.chaiware.acommander.config;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AppRegistryShortcutMatchingTest {

    @Test
    void plainFunctionKeyDoesNotMatchWhenAltOrShiftIsHeld() {
        ActionDefinition view = action("view", "F3", "filePane");

        AppConfig config = new AppConfig();
        config.setActions(List.of(view));
        AppRegistry registry = new AppRegistry(config);

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F3, false, false, false)))
                .isPresent()
                .get()
                .extracting(ActionDefinition::getId)
                .isEqualTo("view");

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F3, false, false, true)))
                .isEmpty();

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F3, true, false, false)))
                .isEmpty();
    }

    @Test
    void modifierSpecificShortcutStillMatchesItsExactCombo() {
        ActionDefinition splitLargeFile = action("splitLargeFile", "Alt+F11", "filePane");

        AppConfig config = new AppConfig();
        config.setActions(List.of(splitLargeFile));
        AppRegistry registry = new AppRegistry(config);

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F11, false, false, true)))
                .isPresent()
                .get()
                .extracting(ActionDefinition::getId)
                .isEqualTo("splitLargeFile");

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F11, false, false, false)))
                .isEmpty();
    }

    @Test
    void modifiedFunctionShortcutDoesNotGetBlockedWhenItIsExplicitlyDefined() {
        ActionDefinition view = action("view", "F3", "filePane");
        ActionDefinition altView = action("altView", "Alt+F3", "filePane");

        AppConfig config = new AppConfig();
        config.setActions(List.of(view, altView));
        AppRegistry registry = new AppRegistry(config);

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F3, false, false, false)))
                .isPresent()
                .get()
                .extracting(ActionDefinition::getId)
                .isEqualTo("view");

        Assertions.assertThat(registry.matchShortcut(ActionScope.FILE_PANE, keyPressed(KeyCode.F3, false, false, true)))
                .isPresent()
                .get()
                .extracting(ActionDefinition::getId)
                .isEqualTo("altView");
    }

    private static ActionDefinition action(String id, String shortcut, String context) {
        ActionDefinition action = new ActionDefinition();
        action.setId(id);
        action.setShortcut(shortcut);
        action.setContexts(List.of(context));
        return action;
    }

    private static KeyEvent keyPressed(KeyCode keyCode, boolean shiftDown, boolean controlDown, boolean altDown) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                keyCode,
                shiftDown,
                controlDown,
                altDown,
                false
        );
    }
}
