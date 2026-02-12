package org.chaiware.acommander.actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ActionMatcher {

    public List<AppAction> rank(String query, List<AppAction> actions, ActionContext context) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AppAction> enabledActions = actions.stream()
                .filter(a -> a.isEnabled(context))
                .toList();

        if (q.isEmpty()) {
            return enabledActions.stream()
                    .sorted(Comparator.comparing(AppAction::title))
                    .toList();
        }

        List<ScoredAction> scored = new ArrayList<>();
        for (AppAction action : enabledActions) {
            int score = score(action, q);
            if (score > 0) {
                scored.add(new ScoredAction(action, score));
            }
        }

        scored.sort(Comparator
                .comparingInt(ScoredAction::score).reversed()
                .thenComparing(sa -> sa.action().title()));

        return scored.stream().map(ScoredAction::action).toList();
    }

    private int score(AppAction action, String query) {
        String title = action.title().toLowerCase(Locale.ROOT);
        if (title.equals(query)) {
            return 1000;
        }
        if (title.startsWith(query)) {
            return 700;
        }
        if (title.contains(query)) {
            return 400;
        }
        for (String alias : action.aliases()) {
            String a = alias.toLowerCase(Locale.ROOT);
            if (a.equals(query)) {
                return 650;
            }
            if (a.startsWith(query)) {
                return 500;
            }
            if (a.contains(query)) {
                return 300;
            }
        }
        return 0;
    }

    private record ScoredAction(AppAction action, int score) {
    }
}
