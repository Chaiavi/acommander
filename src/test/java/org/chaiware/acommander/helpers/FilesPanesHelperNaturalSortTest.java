package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilesPanesHelperNaturalSortTest {

    @Test
    void naturalSortPlaces10After9() {
        List<String> sorted = List.of("file1", "file10", "file2", "file9")
                .stream()
                .sorted(FilesPanesHelper::compareNaturalNames)
                .toList();

        assertEquals(List.of("file1", "file2", "file9", "file10"), sorted);
    }

    @Test
    void naturalSortIsCaseInsensitive() {
        List<String> sorted = List.of("A10", "a2", "a1")
                .stream()
                .sorted(FilesPanesHelper::compareNaturalNames)
                .toList();

        assertEquals(List.of("a1", "a2", "A10"), sorted);
    }

    @Test
    void naturalSortPrefersShorterEqualNumberRun() {
        List<String> sorted = List.of("file02", "file2", "file0002")
                .stream()
                .sorted(FilesPanesHelper::compareNaturalNames)
                .toList();

        assertEquals(List.of("file2", "file02", "file0002"), sorted);
    }
}
