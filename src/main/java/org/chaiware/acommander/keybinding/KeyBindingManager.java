package org.chaiware.acommander.keybinding;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.input.KeyEvent;
import lombok.Setter;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.config.AppRegistry;

import static org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext.*;


public class KeyBindingManager {
    public enum KeyContext {
        FILE_PANE,
        PATH_COMBO_BOX,
        COMMAND_PALETTE,
        GLOBAL,
        DIALOG
    }

    private final Map<KeyContext, IKeyHandler> contextHandlers = new HashMap<>();
    @Setter
    private KeyContext currentContext;

    public KeyBindingManager(Commander commander, AppRegistry appRegistry, ActionExecutor actionExecutor) {
        contextHandlers.put(GLOBAL, new GlobalKeyHandlerImpl(commander, appRegistry, actionExecutor));
        contextHandlers.put(PATH_COMBO_BOX, new ComboxKeyHandlerImpl(commander));
        contextHandlers.put(FILE_PANE, new FilePaneKeyHandlerImpl(commander, appRegistry, actionExecutor));
        contextHandlers.put(COMMAND_PALETTE, new CommandPaletteKeyHandlerImpl(commander));
    }

    public void handleKeyEvent(KeyEvent event) {
        if (!contextHandlers.get(currentContext).handle(event))
            contextHandlers.get(GLOBAL).handle(event);
    }

    /** Global handles keyreleased events (ALT/SHIFT/CTRL) */
    public void handleReleasedKeyEvent(KeyEvent event) {
        ((GlobalKeyHandlerImpl)contextHandlers.get(GLOBAL)).handleKeyReleased(event);
    }
}
