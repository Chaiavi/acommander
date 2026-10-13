package org.chaiware.acommander.services;

import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteConfirmationTest {

    @Test
    void namesOneItemAndCountsSeveral() {
        assertThat(DeleteConfirmation.deleteQuestion(List.of("a.txt"), true)).isEqualTo("Move \"a.txt\" to the Recycle Bin?");
        assertThat(DeleteConfirmation.deleteQuestion(List.of("a.txt", "b"), false))
                .isEqualTo("Permanently delete 2 items? This can't be undone.");
        assertThat(DeleteConfirmation.wipeQuestion(List.of("a.txt"))).isEqualTo("Wipe \"a.txt\"? It is overwritten and can't be recovered.");
        assertThat(DeleteConfirmation.wipeQuestion(List.of("a", "b", "c"))).isEqualTo("Wipe 3 items? They are overwritten and can't be recovered.");
    }

    @Test
    void ftpAndNetworkSharesHaveNoRecycleBin() {
        FtpFileSystem ftp = new FtpFileSystem(FtpConnectionOptions.builder()
                .host("ftp.example.com").port(21).username("u").password("mock").build());

        assertThat(DeleteConfirmation.recycles(ftp, "/pub")).isFalse();
        assertThat(DeleteConfirmation.recycles(new LocalFileSystem(""), "\\\\server\\share")).isFalse();
    }
}
