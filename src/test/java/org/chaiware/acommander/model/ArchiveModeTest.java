package org.chaiware.acommander.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveModeTest {

    @Test
    void supportedExtensionChecksDoNotTreatMediaFilesAsArchives() {
        assertThat(ArchiveMode.isSupportedExtension("zip")).isTrue();
        assertThat(ArchiveMode.isSupportedExtension("rar")).isTrue();
        assertThat(ArchiveMode.isSupportedExtension("mp3")).isFalse();
        assertThat(ArchiveMode.isSupportedExtension("mp4")).isFalse();
    }

    @Test
    void readWriteAndReadOnlyChecksOnlyMatchTheirKnownSets() {
        assertThat(ArchiveMode.isReadWriteExtension("zip")).isTrue();
        assertThat(ArchiveMode.isReadOnlyExtension("rar")).isTrue();
        assertThat(ArchiveMode.isReadWriteExtension("mp3")).isFalse();
        assertThat(ArchiveMode.isReadOnlyExtension("mp3")).isFalse();
    }

    @Test
    void fromExtensionRejectsUnsupportedExtensions() {
        assertThat(ArchiveMode.fromExtension("zip")).isEqualTo(ArchiveMode.READ_WRITE);
        assertThat(ArchiveMode.fromExtension("rar")).isEqualTo(ArchiveMode.READ_ONLY);
        assertThat(ArchiveMode.fromExtension(".ZIP")).isEqualTo(ArchiveMode.READ_WRITE);
        assertThatThrownBy(() -> ArchiveMode.fromExtension("mp3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported archive extension");
    }
}
