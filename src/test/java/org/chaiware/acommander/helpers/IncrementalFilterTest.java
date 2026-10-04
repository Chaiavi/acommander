package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalFilterTest {

    private static final FileItem PARENT = new FileItem(null, "..", 0, 0, true);
    private static final FileItem ALPHA = new FileItem(null, "Alpha.txt", 0, 0, false);
    private static final FileItem ALPS = new FileItem(null, "alps", 0, 0, true);
    private static final FileItem BETA = new FileItem(null, "beta.txt", 0, 0, false);
    private static final List<FileItem> PANE = List.of(PARENT, ALPHA, ALPS, BETA);

    @Test
    void eachCharNarrowsByPrefixIgnoringCaseAndKeepsParent() {
        IncrementalFilter filter = new IncrementalFilter();
        filter.type('A', PANE);
        assertThat(filter.visibleItems()).containsExactly(PARENT, ALPHA, ALPS);

        filter.type('l', filter.visibleItems());
        filter.type('P', filter.visibleItems());
        assertThat(filter.prefix()).isEqualTo("alp");

        filter.type('h', filter.visibleItems());
        assertThat(filter.visibleItems()).containsExactly(PARENT, ALPHA);
    }

    @Test
    void backspaceWidensAndEmptyPrefixEndsTheFilter() {
        IncrementalFilter filter = new IncrementalFilter();
        filter.type('a', PANE);
        filter.type('l', filter.visibleItems());

        filter.backspace();
        assertThat(filter.visibleItems()).containsExactly(PARENT, ALPHA, ALPS);
        filter.backspace();
        assertThat(filter.isActive()).isFalse();
        assertThat(filter.clear()).isEqualTo(PANE);
        assertThat(filter.clear()).isNull();
    }

    @Test
    void startsOverWhenThePaneChangedUnderTheFilter() {
        IncrementalFilter filter = new IncrementalFilter();
        filter.type('a', PANE);

        List<FileItem> refreshed = List.of(PARENT, BETA);
        filter.type('b', refreshed);

        assertThat(filter.prefix()).isEqualTo("b");
        assertThat(filter.visibleItems()).containsExactly(PARENT, BETA);
        assertThat(filter.clear()).isEqualTo(refreshed);
    }
}
