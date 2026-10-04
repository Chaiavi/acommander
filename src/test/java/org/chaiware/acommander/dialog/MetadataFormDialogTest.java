package org.chaiware.acommander.dialog;

import org.chaiware.acommander.dialog.MetadataFormDialog.Field;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataFormDialogTest {

    private static final List<Field> FIELDS = List.of(
            new Field("title", "Title", "-t", "tip"),
            new Field("year", "Year", "-y", "tip"),
            new Field("genre", "Genre", "-g", "tip"));

    @Test
    void changesListsOnlyEditedFieldsAsOptionValuePairs() {
        Map<String, String> loaded = Map.of("title", "Old", "year", "2020");
        Map<String, String> current = Map.of("title", "New", "year", "2020", "genre", "");

        assertThat(MetadataFormDialog.changes(FIELDS, loaded, current)).containsExactly("-t", "New");
    }

    @Test
    void clearingAFieldWritesAnEmptyValue() {
        assertThat(MetadataFormDialog.changes(FIELDS, Map.of("year", "2020"), Map.of("year", "")))
                .containsExactly("-y", "");
    }
}
