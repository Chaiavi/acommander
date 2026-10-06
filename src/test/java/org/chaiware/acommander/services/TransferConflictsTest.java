package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.services.TransferConflicts.Conflict;
import org.chaiware.acommander.services.TransferConflicts.Policy;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConflictsTest {

    @TempDir
    Path tempDir;

    @Test
    void findListsOnlyTheNamesThatExistInTheTargetIgnoringCase() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("source"));
        Path target = Files.createDirectories(tempDir.resolve("target"));
        Path clash = Files.writeString(source.resolve("Report.txt"), "new");
        Path fresh = Files.writeString(source.resolve("fresh.txt"), "x");
        Files.writeString(target.resolve("report.TXT"), "old");
        LocalFileSystem local = new LocalFileSystem("");
        ClipboardTransfer.State state = ClipboardTransfer.capture(List.of(new FileItem(clash), new FileItem(fresh)), false,
                FilesPanesHelper.FocusSide.LEFT, local, source.toString());

        List<Conflict> conflicts = TransferConflicts.find(state, local, target.toString());

        assertThat(conflicts).extracting(Conflict::name).containsExactly("Report.txt");
        assertThat(conflicts.getFirst().source().getSizeInBytes()).isEqualTo(3);
        assertThat(conflicts.getFirst().target().getName()).isEqualTo("report.TXT");
    }

    @Test
    void keepAppliesThePolicyToWholeItems() {
        Entry newerFile = new Entry("newer.txt", false, "/s/newer.txt");
        Entry olderFile = new Entry("older.txt", false, "/s/older.txt");
        Entry folder = new Entry("photos", true, "/s/photos");
        Entry fresh = new Entry("fresh.txt", false, "/s/fresh.txt");
        List<Entry> entries = List.of(newerFile, olderFile, folder, fresh);
        List<Conflict> conflicts = List.of(
                new Conflict("newer.txt", file("newer.txt", 2000), file("newer.txt", 1000)),
                new Conflict("older.txt", file("older.txt", 1000), file("older.txt", 2000)),
                new Conflict("photos", dir("photos"), dir("photos")));

        assertThat(TransferConflicts.keep(entries, conflicts, Policy.OVERWRITE)).isEqualTo(entries);
        assertThat(TransferConflicts.keep(entries, conflicts, Policy.SKIP)).containsExactly(fresh);
        assertThat(TransferConflicts.keep(entries, conflicts, Policy.OVERWRITE_OLDER)).containsExactly(newerFile, fresh);
    }

    @Test
    void eachPolicyHasItsFastCopyMode() {
        assertThat(Policy.OVERWRITE.fastCopyMode()).isEqualTo("force_copy");
        assertThat(Policy.SKIP.fastCopyMode()).isEqualTo("noexist_only");
        assertThat(Policy.OVERWRITE_OLDER.fastCopyMode()).isEqualTo("update");
    }

    private static FileItem file(String name, long modified) {
        return new FileItem(null, name, 10, modified, false);
    }

    private static FileItem dir(String name) {
        return new FileItem(null, name, -1, 0, true);
    }
}
