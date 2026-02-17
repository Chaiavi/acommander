package org.chaiware.acommander.actions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ActionMatcherTest {

    @Test
    void returnsEnabledActionsSortedByTitleWhenQueryBlank() {
        ActionMatcher matcher = new ActionMatcher();
        ActionContext context = new ActionContext(null);

        AppAction alpha = new AppAction("a", "Alpha", "", List.of(), ctx -> true, ctx -> {});
        AppAction beta = new AppAction("b", "Beta", "", List.of(), ctx -> true, ctx -> {});
        AppAction gamma = new AppAction("g", "Gamma", "", List.of(), ctx -> true, ctx -> {});
        AppAction disabled = new AppAction("d", "Delta", "", List.of(), ctx -> false, ctx -> {});

        List<AppAction> ranked = matcher.rank("   ", List.of(gamma, disabled, beta, alpha), context);

        Assertions.assertThat(ranked)
                .extracting(AppAction::title)
                .containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    void ranksByMatchStrengthThenTitle() {
        ActionMatcher matcher = new ActionMatcher();
        ActionContext context = new ActionContext(null);

        AppAction exact = new AppAction("1", "Open", "", List.of(), null, ctx -> {});
        AppAction prefix = new AppAction("2", "Open Folder", "", List.of(), null, ctx -> {});
        AppAction contains = new AppAction("3", "Reopen Tab", "", List.of(), null, ctx -> {});

        List<AppAction> ranked = matcher.rank("open", List.of(contains, prefix, exact), context);

        Assertions.assertThat(ranked)
                .extracting(AppAction::title)
                .containsExactly("Open", "Open Folder", "Reopen Tab");
    }

    @Test
    void aliasMatchCanOutrankWeakerTitleMatch() {
        ActionMatcher matcher = new ActionMatcher();
        ActionContext context = new ActionContext(null);

        AppAction aliasPrefix = new AppAction(
                "1",
                "Remove",
                "",
                List.of("Delete"),
                null,
                ctx -> {}
        );
        AppAction titleContains = new AppAction(
                "2",
                "Deliver Package",
                "",
                List.of(),
                null,
                ctx -> {}
        );

        List<AppAction> ranked = matcher.rank("del", List.of(titleContains, aliasPrefix), context);

        Assertions.assertThat(ranked)
                .extracting(AppAction::title)
                .containsExactly("Remove", "Deliver Package");
    }

    @Test
    void returnsEmptyWhenNoActionsMatch() {
        ActionMatcher matcher = new ActionMatcher();
        ActionContext context = new ActionContext(null);

        AppAction alpha = new AppAction("a", "Alpha", "", List.of(), null, ctx -> {});

        List<AppAction> ranked = matcher.rank("zzz", List.of(alpha), context);

        Assertions.assertThat(ranked).isEmpty();
    }
}
