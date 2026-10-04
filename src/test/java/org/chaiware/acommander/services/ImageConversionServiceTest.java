package org.chaiware.acommander.services;

import org.chaiware.acommander.services.ImageConversionService.ImageCompressionMode;
import org.chaiware.acommander.services.ImageConversionService.ImageConversionRequest;
import org.chaiware.acommander.services.ImageConversionService.ImageResizeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageConversionServiceTest {

    @Test
    void qualityModeWithResizeToPng() {
        ImageConversionRequest request = new ImageConversionRequest("png", ImageCompressionMode.QUALITY, null, null,
                true, false, ImageResizeMode.LONG_EDGE, 1024, true, "_small", "Bigger");

        assertThat(ImageConversionService.buildCommand(Path.of("caesiumclt.exe"), "C:\\out", List.of("a.jpg", "b.jpg"), request))
                .containsExactly("caesiumclt.exe", "--quality", "80", "--output", "C:\\out", "--format", "png",
                        "--exif", "--long-edge", "1024", "--no-upscale", "--png-opt-level", "3", "--zopfli",
                        "--suffix", "_small", "--overwrite", "bigger", "a.jpg", "b.jpg");
    }

    @Test
    void maxSizeWithoutResize() {
        ImageConversionRequest request = new ImageConversionRequest("webp", ImageCompressionMode.MAX_SIZE, null, "200KB",
                false, true, ImageResizeMode.NONE, null, true, "", null);

        assertThat(ImageConversionService.buildCommand(Path.of("c.exe"), "out", List.of("a.png"), request))
                .containsExactly("c.exe", "--max-size", "200KB", "--output", "out", "--format", "webp",
                        "--keep-dates", "--overwrite", "all", "a.png");
    }

    @Test
    void findsTheJpgSpellingOfAJpegOutput(@TempDir Path out) throws IOException {
        Files.writeString(out.resolve("photo_x.jpg"), "x");
        ImageConversionRequest request = new ImageConversionRequest("jpeg", ImageCompressionMode.LOSSLESS, null, null,
                false, false, ImageResizeMode.NONE, null, false, "_x", "all");

        assertThat(ImageConversionService.findFirstConverted(List.of("missing.png", "photo.png"), out.toString(), request))
                .isEqualTo(out.resolve("photo_x.jpg"));
    }
}
