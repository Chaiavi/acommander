package org.chaiware.acommander.helpers;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ImageConversionSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void recognizesConvertibleImageExtensions() throws IOException {
        Path image = Files.createTempFile(tempDir, "photo", ".png");
        Path text = Files.createTempFile(tempDir, "notes", ".txt");

        Assertions.assertThat(ImageConversionSupport.areAllConvertibleImages(List.of(new FileItem(image)))).isTrue();
        Assertions.assertThat(ImageConversionSupport.areAllConvertibleImages(List.of(new FileItem(text)))).isFalse();
    }

    @Test
    void requiresAllSelectedItemsToBeConvertibleImages() throws IOException {
        Path image = Files.createTempFile(tempDir, "photo", ".jpg");
        Path text = Files.createTempFile(tempDir, "notes", ".txt");

        Assertions.assertThat(ImageConversionSupport.areAllConvertibleImages(List.of(
                new FileItem(image),
                new FileItem(text)
        ))).isFalse();
    }

    @Test
    void includesCurrentFormatForSingleSelectionTargets() throws IOException {
        Path image = Files.createTempFile(tempDir, "photo", ".png");

        List<String> targets = ImageConversionSupport.targetFormatsForSelection(List.of(new FileItem(image)));

        Assertions.assertThat(targets).contains("png");
        Assertions.assertThat(targets).contains("jpeg", "webp", "gif", "tiff");
    }
}
