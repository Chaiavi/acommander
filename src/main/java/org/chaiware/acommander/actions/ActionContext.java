package org.chaiware.acommander.actions;

import org.chaiware.acommander.Commander;

public class ActionContext {
    private final Commander commander;

    public ActionContext(Commander commander) {
        this.commander = commander;
    }

    public Commander commander() {
        return commander;
    }
}
