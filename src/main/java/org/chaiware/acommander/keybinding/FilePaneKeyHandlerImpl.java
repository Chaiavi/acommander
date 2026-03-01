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
            commander.clearCharFilter();
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

        Character filterChar = extractFilterChar(event);
        if (filterChar != null) {
            commander.filterByChar(filterChar);
            return true;
        }

        return switch (event.getCode()) {
            case UP, DOWN, LEFT, RIGHT, PAGE_UP, PAGE_DOWN, HOME, END -> false;
            case BACK_SPACE -> {
                if (commander.backspaceCharFilter()) {
                    yield true;
                }
                goUpOneFolder();
                yield true;
            }
            case ENTER -> {
                commander.clearCharFilter();
                commander.enterSelectedItem();
                event.consume();
                yield true;
            }
            default -> {
                commander.clearCharFilter();
                yield false;
            }
        };
    }

    private void goUpOneFolder() {
        // Check if we're in an archive
        if (commander.filesPanesHelper.isInArchive(commander.filesPanesHelper.getFocusedSide())) {
            // Use archive-aware navigation (works like ".." entry)
            commander.filesPanesHelper.goUpInArchive(commander.filesPanesHelper.getFocusedSide());
        } else {
            // Regular folder navigation
            File parent = new File(commander.filesPanesHelper.getFocusedPath()).getParentFile();
            if (parent != null)
                commander.filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
        }
    }

    private Character extractFilterChar(KeyEvent event) {
        String keyText = event.getText();
        if (keyText != null && keyText.codePointCount(0, keyText.length()) == 1) {
            int codePoint = keyText.codePointAt(0);
            if (Character.isLetterOrDigit(codePoint)) {
                return Character.toLowerCase((char) codePoint);
            }
        }

        if (event.getCode().isLetterKey()) {
            String keyName = event.getCode().getName();
            if (keyName != null && keyName.length() == 1) {
                return Character.toLowerCase(keyName.charAt(0));
            }
        }

        return switch (event.getCode()) {
            case DIGIT0, NUMPAD0 -> '0';
            case DIGIT1, NUMPAD1 -> '1';
            case DIGIT2, NUMPAD2 -> '2';
            case DIGIT3, NUMPAD3 -> '3';
            case DIGIT4, NUMPAD4 -> '4';
            case DIGIT5, NUMPAD5 -> '5';
            case DIGIT6, NUMPAD6 -> '6';
            case DIGIT7, NUMPAD7 -> '7';
            case DIGIT8, NUMPAD8 -> '8';
            case DIGIT9, NUMPAD9 -> '9';
            default -> null;
        };
    }
}
