package org.chaiware.acommander.dialog;

import org.chaiware.acommander.dialog.MetadataFormDialog.Field;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataFormDialogTest {

    private static final List<Field> FIELDS = List.of(
            new Field("title", "Title", "tip"),
            new Field("date", "Year", "tip"),
            new Field("genre", "Genre", "tip"));

    @Test
    void changesListsOnlyEditedFields() {
        Map<String, String> loaded = Map.of("title", "Old", "date", "2020");
        Map<String, String> current = Map.of("title", "New", "date", "2020", "genre", "");

        assertThat(MetadataFormDialog.changes(FIELDS, loaded, current)).containsExactly(Map.entry("title", "New"));
    }

    @Test
    void clearingAFieldWritesAnEmptyValue() {
        assertThat(MetadataFormDialog.changes(FIELDS, Map.of("date", "2020"), Map.of("date", "")))
                .containsExactly(Map.entry("date", ""));
    }
}
