package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.SelectionRule;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;

import static javafx.scene.input.KeyCode.*;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

public class GlobalKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;
    private final AppRegistry appRegistry;
    private final ActionExecutor actionExecutor;

    public GlobalKeyHandlerImpl(Commander commander, AppRegistry appRegistry, ActionExecutor actionExecutor) {
        this.commander = commander;
        this.appRegistry = appRegistry;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public boolean handle(KeyEvent event) {
        logger.trace("Key event phase: {}", event.getEventType());
        logger.trace("Event target: {}", event.getTarget());
        logger.trace("Event source: {}", event.getSource());

        ActionDefinition action = appRegistry.matchShortcut(ActionScope.GLOBAL, event).orElse(null);
        if (action != null) {
            SelectionRule rule = SelectionRule.fromString(action.getSelection());
            if (rule.isSatisfied(commander.filesPanesHelper.getSelectedItems())) {
                actionExecutor.execute(action);
            }
            return true;
        }
        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.updateBottomButtons(event.getCode());
            return false;
        }

        return switch (event.getCode()) {
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
            if (commander.filesPanesHelper.getFocusedSide() == LEFT) {
                ensureSelection(commander.rightFileList);
                commander.rightFileList.requestFocus();
            } else {
                ensureSelection(commander.leftFileList);
                commander.leftFileList.requestFocus();
            }
        } else {
            ensureSelection(commander.leftFileList);
            commander.leftFileList.requestFocus();
        }
    }

    private void ensureSelection(javafx.scene.control.ListView<?> listView) {
        if (!listView.getItems().isEmpty() && listView.getSelectionModel().getSelectedIndex() < 0) {
            listView.getSelectionModel().selectFirst();
        }
    }
}
