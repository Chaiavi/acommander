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

    @Test
    void onlyTheNewestListingIsShownAndSelectionWaitsForIt() {
        FilesPanesHelper.Loads loads = new FilesPanesHelper.Loads();
        org.chaiware.acommander.vfs.VFileSystem fs = new org.chaiware.acommander.vfs.LocalFileSystem("");
        List<String> ran = new ArrayList<>();

        long slowA = loads.start();
        long fastB = loads.start();
        loads.whenShown(() -> ran.add("select in B"));

        assertThat(loads.isLatest(slowA)).as("A finishing late must not replace B").isFalse();
        assertThat(ran).as("nothing shown yet").isEmpty();

        loads.shown(fastB, fs, "B");
        assertThat(ran).containsExactly("select in B");
        assertThat(loads.shows(fs, "B")).isTrue();

        loads.whenShown(() -> ran.add("now"));
        assertThat(ran).as("no listing pending: runs at once").containsExactly("select in B", "now");
    }

    @Test
    void aWaitingSelectionSkipsAListingThatIsAlreadyOutdated() {
        FilesPanesHelper.Loads loads = new FilesPanesHelper.Loads();
        org.chaiware.acommander.vfs.VFileSystem fs = new org.chaiware.acommander.vfs.LocalFileSystem("");
        List<String> ran = new ArrayList<>();

        long first = loads.start();
        loads.whenShown(() -> ran.add("select"));
        long second = loads.start();

        loads.shown(first, fs, "A");
        assertThat(ran).as("a newer listing is coming").isEmpty();
        loads.shown(second, fs, "A");
        assertThat(ran).containsExactly("select");
        assertThat(loads.shows(new org.chaiware.acommander.vfs.LocalFileSystem(""), "A")).as("another file system").isFalse();
    }
}
