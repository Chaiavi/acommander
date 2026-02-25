package org.chaiware.acommander.actions;

import org.chaiware.acommander.helpers.AudioConversionSupport;
import org.chaiware.acommander.helpers.ImageConversionSupport;
import org.chaiware.acommander.model.FileItem;

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
                    .sorted(Comparator
                            .comparingInt((AppAction action) -> pinnedConversionPriority(action, context))
                            .thenComparing(AppAction::title))
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
        for (String alias : action.aliases()) {
            String a = alias.toLowerCase(Locale.ROOT);
            if (a.equals(query)) {
                return 900;
            }
        }
        if (title.startsWith(query)) {
            return 700;
        }
        if (title.contains(query)) {
            return 400;
        }
        for (String alias : action.aliases()) {
            String a = alias.toLowerCase(Locale.ROOT);
            if (a.startsWith(query)) {
                return 800;
            }
            if (a.contains(query)) {
                return 300;
            }
        }
        return 0;
    }

    private record ScoredAction(AppAction action, int score) {
    }

    private int pinnedConversionPriority(AppAction action, ActionContext context) {
        if (action == null || context == null || context.commander() == null || context.commander().filesPanesHelper == null) {
            return 1;
        }
        List<FileItem> selectedItems = context.commander().filesPanesHelper.getSelectedItems();
        if ("convertAudioFiles".equals(action.id()) && AudioConversionSupport.areAllConvertibleAudio(selectedItems)) {
            return 0;
        }
        if ("convertGraphicsFiles".equals(action.id()) && ImageConversionSupport.areAllConvertibleImages(selectedItems)) {
            return 0;
        }
        return 1;
    }
}
