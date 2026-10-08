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

        assertThat(ImageConversionService.buildCommands(Path.of("caesiumclt.exe"), "C:\\out", List.of("a.jpg", "b.jpg"), request))
                .containsExactly(List.of("caesiumclt.exe", "--quality", "80", "--output", "C:\\out", "--format", "png",
                        "--verbose", "2", "--exif", "--long-edge", "1024", "--no-upscale", "--png-opt-level", "3", "--zopfli",
                        "--suffix", "_small", "--overwrite", "bigger", "a.jpg", "b.jpg"));
    }

    @Test
    void filesAlreadyInTheTargetFormatGetTheirOwnRunThatKeepsTheFormat() {
        ImageConversionRequest request = new ImageConversionRequest("jpeg", ImageCompressionMode.QUALITY, 70, null,
                false, false, ImageResizeMode.NONE, null, false, "", "all");

        assertThat(ImageConversionService.buildCommands(Path.of("c.exe"), "out", List.of("a.png", "b.JPG", "c.jpeg", "d.gif"), request))
                .containsExactly(
                        List.of("c.exe", "--quality", "70", "--output", "out", "--format", "jpeg", "--verbose", "2",
                                "--overwrite", "all", "a.png", "d.gif"),
                        List.of("c.exe", "--quality", "70", "--output", "out", "--format", "original", "--verbose", "2",
                                "--overwrite", "all", "b.JPG", "c.jpeg"));
    }

    @Test
    void failuresAreCaesiumsErrorLines() {
        // Real caesiumclt 1.2.0 output (--verbose 2) for a PNG converted to PNG
        List<String> output = List.of("[Error] C:\\in\\big.png -> C:\\out\\big.png", "1.3 KiB -> 0 B [-1.3 KiB | -100.00%]",
                "Error compressing file: Cannot convert to the same format [10407]", "",
                "Compressed 1 files (0 success, 0 skipped, 1 errors)");

        assertThat(ImageConversionService.failures(output)).containsExactly("[Error] C:\\in\\big.png -> C:\\out\\big.png",
                "Error compressing file: Cannot convert to the same format [10407]");
    }

    @Test
    void maxSizeWithoutResize() {
        ImageConversionRequest request = new ImageConversionRequest("webp", ImageCompressionMode.MAX_SIZE, null, "200KB",
                false, true, ImageResizeMode.NONE, null, true, "", null);

        assertThat(ImageConversionService.buildCommands(Path.of("c.exe"), "out", List.of("a.png"), request))
                .containsExactly(List.of("c.exe", "--max-size", "200KB", "--output", "out", "--format", "webp",
                        "--verbose", "2", "--keep-dates", "--overwrite", "all", "a.png"));
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
