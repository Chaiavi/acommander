package org.chaiware.acommander.keybinding;

import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.SelectionRule;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.vfs.FtpFileSystem;

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
        // Sync tracked state with actual event state to prevent "stuck" modifiers
        syncModifierState(event);
        
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

        // Handle numpad operator keys for selection operations
        if (handleNumpadSelectionKeys(event)) {
            return true;
        }

        if (event.isAltDown() || event.isShiftDown() || event.isControlDown()) {
            return false;
        }

        // ALT or SHIFT for bottom buttons
        if (event.getCode() == ALT || event.getCode() == SHIFT || event.getCode() == CONTROL) {
            commander.activeModifiers.add(event.getCode());
            commander.updateBottomButtons();
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
        FilesPanesHelper.FocusSide side = commander.filesPanesHelper.getFocusedSide();
        // Check if we're in FTP
        if (commander.filesPanesHelper.getFileSystem(side) instanceof FtpFileSystem ftpFs) {
            String currentPath = commander.filesPanesHelper.getPath(side);

            if ("/".equals(currentPath) || currentPath.isEmpty()) {
                // At FTP root, backspace disconnects and goes back to local
                commander.ftpDisconnect();
            } else {
                // Not at root, go up one level in FTP
                String parentPath = ftpFs.getParent(currentPath);
                commander.filesPanesHelper.setFileListPath(side, parentPath);
            }
            return;
        }

        // Check if we're in an archive
        if (commander.filesPanesHelper.isInArchive(side)) {
            // Use archive-aware navigation (works like ".." entry)
            commander.filesPanesHelper.goUpInArchive(side);
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

    /**
     * Handles numpad operator keys for selection operations.
     * Num + → select by pattern
     * Num - → unselect all
     * Num * → invert selection
     */
    private boolean handleNumpadSelectionKeys(KeyEvent event) {
        // Only handle if no modifiers are pressed
        if (event.isAltDown() || event.isShiftDown() || event.isControlDown()) {
            return false;
        }

        return switch (event.getCode()) {
            case ADD -> {
                commander.selectByPattern();
                yield true;
            }
            case SUBTRACT -> {
                commander.unselectAll();
                yield true;
            }
            case MULTIPLY -> {
                commander.invertSelection();
                yield true;
            }
            default -> false;
        };
    }
    
    /**
     * Syncs the tracked modifier state with the actual state from the event.
     * This prevents "stuck" modifier states when key events are missed due to focus changes.
     */
    private void syncModifierState(KeyEvent event) {
        boolean altDown = event.isAltDown();
        boolean shiftDown = event.isShiftDown();
        boolean controlDown = event.isControlDown();
        
        if (altDown != commander.activeModifiers.contains(javafx.scene.input.KeyCode.ALT)) {
            if (altDown) {
                commander.activeModifiers.add(javafx.scene.input.KeyCode.ALT);
            } else {
                commander.activeModifiers.remove(javafx.scene.input.KeyCode.ALT);
            }
        }
        if (shiftDown != commander.activeModifiers.contains(javafx.scene.input.KeyCode.SHIFT)) {
            if (shiftDown) {
                commander.activeModifiers.add(javafx.scene.input.KeyCode.SHIFT);
            } else {
                commander.activeModifiers.remove(javafx.scene.input.KeyCode.SHIFT);
            }
        }
        if (controlDown != commander.activeModifiers.contains(javafx.scene.input.KeyCode.CONTROL)) {
            if (controlDown) {
                commander.activeModifiers.add(javafx.scene.input.KeyCode.CONTROL);
            } else {
                commander.activeModifiers.remove(javafx.scene.input.KeyCode.CONTROL);
            }
        }
    }
}
