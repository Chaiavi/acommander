package org.chaiware.acommander.helpers;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class AudioConversionSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void recognizesConvertibleAudioExtensions() throws IOException {
        Path audio = Files.createTempFile(tempDir, "sound", ".wav");
        Path text = Files.createTempFile(tempDir, "notes", ".txt");

        Assertions.assertThat(AudioConversionSupport.isConvertibleAudio(new FileItem(audio.toFile()))).isTrue();
        Assertions.assertThat(AudioConversionSupport.isConvertibleAudio(new FileItem(text.toFile()))).isFalse();
    }

    @Test
    void requiresAllSelectedItemsToBeConvertibleAudio() throws IOException {
        Path audio = Files.createTempFile(tempDir, "sound", ".flac");
        Path text = Files.createTempFile(tempDir, "notes", ".txt");

        Assertions.assertThat(AudioConversionSupport.areAllConvertibleAudio(List.of(
                new FileItem(audio.toFile()),
                new FileItem(text.toFile())
        ))).isFalse();
    }

    @Test
    void exposesCommonAudioTargets() throws IOException {
        Path audio = Files.createTempFile(tempDir, "sound", ".wav");

        List<String> targets = AudioConversionSupport.targetFormatsForSelection(List.of(new FileItem(audio.toFile())));

        Assertions.assertThat(targets).contains("wav", "flac", "ogg", "opus", "mp3");
    }
}
