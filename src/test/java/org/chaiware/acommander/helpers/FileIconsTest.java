package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileIconsTest {

    private static String glyph(String name, boolean directory) {
        return FileIcons.of(new FileItem(null, name, 0, 0, directory)).glyph();
    }

    @Test
    void picksTheIconByKind() {
        assertThat(glyph("photos", true)).isEqualTo("📁");
        assertThat(glyph("backup.ZIP", false)).isEqualTo("📦");
        assertThat(glyph("manual.pdf", false)).isEqualTo("📕");
        assertThat(glyph("notes.md", false)).isEqualTo("📄");
        assertThat(glyph("song.mp3", false)).isEqualTo("🎵");
        assertThat(glyph("setup.exe", false)).isEqualTo("⚙");
    }

    @Test
    void unknownOrMissingExtensionGetsThePlainFileIcon() {
        assertThat(glyph("data.xyz", false)).isEqualTo("📃");
        assertThat(glyph("Makefile", false)).isEqualTo("📃");
        assertThat(glyph("trailing.", false)).isEqualTo("📃");
    }
}
