package org.chaiware.acommander.helpers;

import org.chaiware.acommander.helpers.PaneSorter.SortColumn;
import org.chaiware.acommander.helpers.PaneSorter.SortState;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaneSorterTest {

    @Test
    void naturalSortPlaces10After9() {
        assertThat(List.of("file1", "file10", "file2", "file9").stream().sorted(PaneSorter::compareNaturalNames))
                .containsExactly("file1", "file2", "file9", "file10");
    }

    @Test
    void naturalSortIsCaseInsensitive() {
        assertThat(List.of("A10", "a2", "a1").stream().sorted(PaneSorter::compareNaturalNames))
                .containsExactly("a1", "a2", "A10");
    }

    @Test
    void naturalSortPrefersShorterEqualNumberRun() {
        assertThat(List.of("file02", "file2", "file0002").stream().sorted(PaneSorter::compareNaturalNames))
                .containsExactly("file2", "file02", "file0002");
    }

    @Test
    void parentEntryStaysFirstAndFoldersComeBeforeFiles() {
        FileItem parent = new FileItem(null, "..", 0, 0, true);
        FileItem big = new FileItem(null, "big.txt", 900, 1, false);
        FileItem small = new FileItem(null, "small.txt", 10, 2, false);
        FileItem folder = new FileItem(null, "zeta", 0, 3, true);

        assertThat(PaneSorter.sort(List.of(big, folder, parent, small), new SortState(SortColumn.SIZE, false)))
                .containsExactly(parent, folder, big, small);
        assertThat(PaneSorter.sort(List.of(small, folder, parent, big), new SortState(SortColumn.MODIFIED, true)))
                .containsExactly(parent, folder, big, small);
    }

    @Test
    void togglingFlipsTheActiveColumnAndStartsModifiedNewestFirst() {
        assertThat(SortState.DEFAULT.toggle(SortColumn.NAME)).isEqualTo(new SortState(SortColumn.NAME, false));
        assertThat(SortState.DEFAULT.toggle(SortColumn.SIZE)).isEqualTo(new SortState(SortColumn.SIZE, true));
        assertThat(SortState.DEFAULT.toggle(SortColumn.MODIFIED)).isEqualTo(new SortState(SortColumn.MODIFIED, false));
    }
}
