package org.chaiware.acommander.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private List<ActionDefinition> actions = new ArrayList<>();

    public List<ActionDefinition> getActions() {
        return actions;
    }

    public void setActions(List<ActionDefinition> actions) {
        this.actions = actions == null ? new ArrayList<>() : actions;
    }
}
