package org.chaiware.acommander.actions;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class AppAction {
    private final String id;
    private final String title;
    private final Function<ActionContext, String> dynamicTitle;
    private final String shortcut;
    private final List<String> aliases;
    private final Function<ActionContext, Integer> priority;
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
        this(id, title, null, shortcut, aliases, null, enabled, execute);
    }

    public AppAction(
            String id,
            String title,
            String shortcut,
            List<String> aliases,
            Function<ActionContext, Integer> priority,
            Predicate<ActionContext> enabled,
            Consumer<ActionContext> execute
    ) {
        this(id, title, null, shortcut, aliases, priority, enabled, execute);
    }

    public AppAction(
            String id,
            String title,
            Function<ActionContext, String> dynamicTitle,
            String shortcut,
            List<String> aliases,
            Function<ActionContext, Integer> priority,
            Predicate<ActionContext> enabled,
            Consumer<ActionContext> execute
    ) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.dynamicTitle = dynamicTitle;
        this.shortcut = shortcut == null ? "" : shortcut;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.priority = priority == null ? ctx -> 0 : priority;
        this.enabled = enabled == null ? ctx -> true : enabled;
        this.execute = Objects.requireNonNull(execute);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String title(ActionContext context) {
        if (dynamicTitle != null) {
            String dynamic = dynamicTitle.apply(context);
            if (dynamic != null) {
                return dynamic;
            }
        }
        return title;
    }

    public String shortcut() {
        return shortcut;
    }

    public List<String> aliases() {
        return aliases;
    }

    public int priority(ActionContext context) {
        return priority.apply(context);
    }

    public boolean isEnabled(ActionContext context) {
        return enabled.test(context);
    }

    public void run(ActionContext context) {
        execute.accept(context);
    }
}
