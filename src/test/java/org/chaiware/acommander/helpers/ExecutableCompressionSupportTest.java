package org.chaiware.acommander.helpers;

import org.chaiware.acommander.helpers.ExecutableCompressionSupport.UpxAction;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutableCompressionSupportTest {

    @Test
    void upxCommandPutsTheActionFlagBeforeTheFiles() {
        File app = new File("app.exe");
        assertThat(ExecutableCompressionSupport.upxCommand(Path.of("upx.exe"), UpxAction.DECOMPRESS, List.of(app)))
                .containsExactly("upx.exe", "-d", app.getAbsolutePath());
        assertThat(ExecutableCompressionSupport.upxCommand(Path.of("upx.exe"), UpxAction.BEST, List.of(app)))
                .containsExactly("upx.exe", "--ultra-brute", app.getAbsolutePath());
    }

    @Test
    void percentChangeIsNegativeWhenTheFileShrank() {
        assertThat(ExecutableCompressionSupport.percentChange(800, 500)).isEqualTo("-37.50%");
        assertThat(ExecutableCompressionSupport.percentChange(0, 500)).isEqualTo("N/A");
    }
}
