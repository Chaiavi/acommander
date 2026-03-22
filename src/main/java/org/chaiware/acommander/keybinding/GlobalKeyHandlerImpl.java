package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyCode;
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

        // Sync tracked state with actual event state to prevent "stuck" modifiers
        syncModifierState(event);

        ActionDefinition action = appRegistry.matchShortcut(ActionScope.GLOBAL, event).orElse(null);
        if (action != null) {
            SelectionRule rule = SelectionRule.fromString(action.getSelection());
            if (rule.isSatisfied(commander.filesPanesHelper.getSelectedItems())) {
                actionExecutor.execute(action);
            } else {
                int selectedCount = commander.filesPanesHelper.getSelectedItems() == null
                        ? 0
                        : commander.filesPanesHelper.getSelectedItems().size();
                logger.info(
                        "Shortcut matched but selection rule blocked action: id={}, shortcut={}, rule={}, selectedCount={}",
                        action.getId(),
                        action.getShortcut(),
                        rule,
                        selectedCount
                );
            }
            return true;
        }
        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.activeModifiers.add(event.getCode());
            commander.updateBottomButtons();
            return false;
        }

        return switch (event.getCode()) {
            case TAB -> { clickTab(); event.consume(); yield true; }
            default -> false;
        };
    }


    public void handleKeyReleased(KeyEvent event) {
        // Remove the released modifier key
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.activeModifiers.remove(event.getCode());
        }
        
        // Sync tracked state with actual event state to prevent "stuck" modifiers
        // This handles cases where focus changes might cause key release events to be missed
        syncModifierState(event);
        
        commander.updateBottomButtons();
    }
    
    /**
     * Syncs the tracked modifier state with the actual state from the event.
     * This prevents "stuck" modifier states when key events are missed due to focus changes.
     */
    private void syncModifierState(KeyEvent event) {
        boolean altDown = event.isAltDown();
        boolean shiftDown = event.isShiftDown();
        boolean controlDown = event.isControlDown();
        
        if (altDown != commander.activeModifiers.contains(KeyCode.ALT)) {
            if (altDown) {
                commander.activeModifiers.add(KeyCode.ALT);
            } else {
                commander.activeModifiers.remove(KeyCode.ALT);
            }
        }
        if (shiftDown != commander.activeModifiers.contains(KeyCode.SHIFT)) {
            if (shiftDown) {
                commander.activeModifiers.add(KeyCode.SHIFT);
            } else {
                commander.activeModifiers.remove(KeyCode.SHIFT);
            }
        }
        if (controlDown != commander.activeModifiers.contains(KeyCode.CONTROL)) {
            if (controlDown) {
                commander.activeModifiers.add(KeyCode.CONTROL);
            } else {
                commander.activeModifiers.remove(KeyCode.CONTROL);
            }
        }
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
