package org.chaiware.acommander.actions;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionRuleTest {
    private static final FileItem FILE_A = new FileItem(null, "a.txt", 1, 0, false);
    private static final FileItem FILE_B = new FileItem(null, "b.txt", 1, 0, false);
    private static final FileItem FOLDER = new FileItem(null, "dir", 0, 0, true);

    @Test
    void blockedMessageSaysWhatIsNeededAndHowManyAreSelected() {
        assertThat(SelectionRule.SINGLE.blockedMessage("File Properties", List.of(FILE_A, FILE_B)))
                .isEqualTo("File Properties needs exactly one selected item (2 selected)");
        assertThat(SelectionRule.MULTI.blockedMessage("Merge PDF", List.of(FILE_A)))
                .isEqualTo("Merge PDF needs two or more selected items (1 selected)");
        assertThat(SelectionRule.ANY.blockedMessage("Delete", null))
                .isEqualTo("Delete needs at least one selected item (0 selected)");
        assertThat(SelectionRule.SINGLE_OR_MULTIPLE_FILES.blockedMessage("Compress", List.of(FILE_A, FOLDER)))
                .isEqualTo("Compress needs selected files only, no folders (2 selected)");
    }

    @Test
    void blockedMessageIsNullWhenTheRuleHolds() {
        assertThat(SelectionRule.NONE.blockedMessage("Help", List.of())).isNull();
        assertThat(SelectionRule.SINGLE_FILE.blockedMessage("Analyze File", List.of(FILE_A))).isNull();
        assertThat(SelectionRule.SINGLE_FOLDER.blockedMessage("X", List.of(FILE_A))).isNotNull();
    }
}
