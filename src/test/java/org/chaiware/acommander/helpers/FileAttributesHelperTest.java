package org.chaiware.acommander.helpers;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;

class FileAttributesHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void toAttribFlagsIncludesAllAttributesInOrder() {
        FileAttributesHelper.AttributeChangeRequest request =
                new FileAttributesHelper.AttributeChangeRequest(true, false, true, false);

        Assertions.assertThat(request.toAttribFlags())
                .containsExactly("+R", "-H", "+S", "-A");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void applyAttributesWithFallbackUpdatesDosAttributes() throws Exception {
        Path file = Files.createTempFile(tempDir, "attrs", ".txt");
        FileAttributesHelper helper = new FileAttributesHelper();

        FileAttributesHelper.AttributeChangeRequest setAttrs =
                new FileAttributesHelper.AttributeChangeRequest(true, true, false, false);
        helper.applyAttributesWithFallback(file, setAttrs);

        DosFileAttributes attrs = Files.readAttributes(file, DosFileAttributes.class);
        Assertions.assertThat(attrs.isReadOnly()).isTrue();
        Assertions.assertThat(attrs.isHidden()).isTrue();

        FileAttributesHelper.AttributeChangeRequest clearAttrs =
                new FileAttributesHelper.AttributeChangeRequest(false, false, false, false);
        helper.applyAttributesWithFallback(file, clearAttrs);
    }
}
