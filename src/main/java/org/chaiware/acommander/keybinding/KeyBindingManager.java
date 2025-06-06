package org.chaiware.acommander.keybinding;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.Commander;

import static org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext.*;


public class KeyBindingManager {
    public enum KeyContext {
        FILE_PANE,
        PATH_COMBO_BOX,
        COMMAND_PALETTE,
        GLOBAL
    }

    private final Map<KeyContext, IKeyHandler> contextHandlers = new HashMap<>();
    private KeyContext currentContext = KeyContext.GLOBAL;

    public KeyBindingManager(Commander commander) {
        contextHandlers.put(GLOBAL, new GlobalKeyHandlerImpl(commander));
        contextHandlers.put(PATH_COMBO_BOX, new ComboxKeyHandlerImpl(commander));
        contextHandlers.put(FILE_PANE, new FilePaneKeyHandlerImpl(commander));
    }

    public void handleKeyEvent(KeyEvent event) {
        if (!contextHandlers.get(currentContext).handle(event))
            contextHandlers.get(GLOBAL).handle(event);
    }

    public void setCurrentContext(KeyContext keyContext) {
        this.currentContext = keyContext;
    }
}
