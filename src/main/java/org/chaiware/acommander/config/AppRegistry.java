package org.chaiware.acommander.config;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.*;
import java.util.stream.Collectors;

public class AppRegistry {
    private final List<ActionDefinition> actions;
    private final Map<ActionScope, List<ActionDefinition>> actionsByScope;
    private final Map<String, KeyCombination> shortcutCache;

    public AppRegistry(AppConfig config) {
        actions = List.copyOf(config.getActions());
        actionsByScope = new EnumMap<>(ActionScope.class);
        for (ActionScope scope : ActionScope.values()) {
            List<ActionDefinition> scopedActions = actions.stream()
                    .filter(action -> action.getContexts().stream()
                            .anyMatch(ctx -> ActionScope.fromId(ctx).orElse(null) == scope))
                    .collect(Collectors.toList());
            actionsByScope.put(scope, scopedActions);
        }
        shortcutCache = new HashMap<>();
        for (ActionDefinition action : actions) {
            if (action.getShortcut() != null && !action.getShortcut().isBlank()) {
                KeyCombination combo = parseShortcut(action.getShortcut());
                if (combo != null) {
                    shortcutCache.put(action.getId(), combo);
                }
            }
        }
    }

    public List<ActionDefinition> actionsForScope(ActionScope scope) {
        return actionsByScope.getOrDefault(scope, List.of());
    }

    public Optional<ActionDefinition> findAction(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return actions.stream()
                .filter(action -> id.equals(action.getId()))
                .findFirst();
    }

    public Optional<ActionDefinition> matchShortcut(ActionScope scope, KeyEvent event) {
        for (ActionDefinition action : actionsForScope(scope)) {
            KeyCombination combo = shortcutCache.get(action.getId());
            if (combo != null && combo.match(event)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    private KeyCombination parseShortcut(String shortcut) {
        if (shortcut == null || shortcut.isBlank()) {
            return null;
        }
        String[] tokens = shortcut.split("\\+");
        if (tokens.length == 0) {
            return null;
        }
        KeyCombination.ModifierValue shift = KeyCombination.ModifierValue.UP;
        KeyCombination.ModifierValue alt = KeyCombination.ModifierValue.UP;
        KeyCombination.ModifierValue ctrl = KeyCombination.ModifierValue.UP;
        KeyCombination.ModifierValue meta = KeyCombination.ModifierValue.UP;
        KeyCombination.ModifierValue shortcutKey = KeyCombination.ModifierValue.UP;
        String keyToken = null;
        for (String raw : tokens) {
            String token = raw.trim();
            if (token.equalsIgnoreCase("shift")) {
                shift = KeyCombination.ModifierValue.DOWN;
            } else if (token.equalsIgnoreCase("alt")) {
                alt = KeyCombination.ModifierValue.DOWN;
            } else if (token.equalsIgnoreCase("ctrl") || token.equalsIgnoreCase("control")) {
                ctrl = KeyCombination.ModifierValue.DOWN;
            } else if (token.equalsIgnoreCase("meta") || token.equalsIgnoreCase("win") || token.equalsIgnoreCase("windows")) {
                meta = KeyCombination.ModifierValue.DOWN;
            } else if (token.equalsIgnoreCase("shortcut")) {
                shortcutKey = KeyCombination.ModifierValue.DOWN;
            } else {
                keyToken = token;
            }
        }
        if (keyToken == null) {
            return null;
        }
        try {
            KeyCode keyCode = KeyCode.valueOf(keyToken.toUpperCase());
            return new KeyCodeCombination(
                    keyCode,
                    shift,
                    ctrl,
                    alt,
                    meta,
                    shortcutKey
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
