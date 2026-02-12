package org.chaiware.acommander.actions;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AppAction {
    private final String id;
    private final String title;
    private final String shortcut;
    private final List<String> aliases;
    private final Predicate<ActionContext> enabled;
    private final Consumer<ActionContext> execute;

    public AppAction(
            String id,
            String title,
            String shortcut,
            List<String> aliases,
            Predicate<ActionContext> enabled,
            Consumer<ActionContext> execute
    ) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.shortcut = shortcut == null ? "" : shortcut;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.enabled = enabled == null ? ctx -> true : enabled;
        this.execute = Objects.requireNonNull(execute);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String shortcut() {
        return shortcut;
    }

    public List<String> aliases() {
        return aliases;
    }

    public boolean isEnabled(ActionContext context) {
        return enabled.test(context);
    }

    public void run(ActionContext context) {
        execute.accept(context);
    }
}
