package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.SelectionRule;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;

import java.io.File;

import static javafx.scene.input.KeyCode.*;


public class FilePaneKeyHandlerImpl implements IKeyHandler {
    private final Commander commander;
    private final AppRegistry appRegistry;
    private final ActionExecutor actionExecutor;

    public FilePaneKeyHandlerImpl(Commander commander, AppRegistry appRegistry, ActionExecutor actionExecutor) {
        this.commander = commander;
        this.appRegistry = appRegistry;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public boolean handle(KeyEvent event) {
        ActionDefinition action = appRegistry.matchShortcut(ActionScope.FILE_PANE, event).orElse(null);
        if (action != null) {
            SelectionRule rule = SelectionRule.fromString(action.getSelection());
            if (rule.isSatisfied(commander.filesPanesHelper.getSelectedItems())) {
                String builtin = action.getBuiltin() == null ? action.getId() : action.getBuiltin();
                if ("view".equals(builtin) && commander.filesPanesHelper.getSelectedItem().isDirectory()) {
                    commander.calculateDirSpace();
                } else {
                    actionExecutor.execute(action);
                }
            }
            return true;
        }

        if (event.isAltDown() || event.isShiftDown() || event.isControlDown()) {
            return false;
        }

        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.updateBottomButtons(event.getCode());
            return false;
        }

        return switch (event.getCode()) {
            case BACK_SPACE -> {
                goUpOneFolder();
                yield true;
            }
            case ENTER -> {
                commander.enterSelectedItem();
                event.consume();
                yield true;
            }
            default -> {
                String keyText = event.getText();
                if (keyText.matches("[a-zA-Z0-9]")) {
                    commander.filterByChar(keyText.charAt(0));
                    yield true;
                }
                yield false;
            }
        };
    }

    private void goUpOneFolder() {
        File parent = new File(commander.filesPanesHelper.getFocusedPath()).getParentFile();
        if (parent != null)
            commander.filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
    }
}
