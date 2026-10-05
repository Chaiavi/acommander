package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FilesPanesHelperTest {

    private static List<FileItem> items(String... names) {
        List<FileItem> items = new ArrayList<>();
        for (String name : names) {
            items.add(new FileItem(null, name, 0, 0, name.startsWith("dir")));
        }
        return items;
    }

    @Test
    void invertSelectsTheUnselectedItemsButNeverTheParentEntry() {
        List<FileItem> pane = items("..", "dir1", "a.txt", "b.txt");

        assertThat(FilesPanesHelper.invertedIndices(pane, List.of())).containsExactly(1, 2, 3);
        assertThat(FilesPanesHelper.invertedIndices(pane, List.of(1, 3))).containsExactly(2);
        assertThat(FilesPanesHelper.invertedIndices(pane, List.of(0, 1, 2, 3))).isEmpty();
        assertThat(FilesPanesHelper.invertedIndices(List.of(), List.of())).isEmpty();
    }

    @Test
    void invertWalksTheListOnceWithoutSearchingIt() {
        List<FileItem> pane = new ArrayList<>(items(IntStream.range(0, 20_000).mapToObj(i -> "f" + i).toArray(String[]::new))) {
            @Override
            public int indexOf(Object o) {
                throw new AssertionError("indexOf makes inverting quadratic");
            }
        };
        List<Integer> selected = IntStream.range(0, 20_000).filter(i -> i % 2 == 0).boxed().toList();

        int[] inverted = FilesPanesHelper.invertedIndices(pane, selected);

        assertThat(inverted).hasSize(10_000);
        assertThat(inverted[0]).isEqualTo(1);
        assertThat(inverted[inverted.length - 1]).isEqualTo(19_999);
    }
}
