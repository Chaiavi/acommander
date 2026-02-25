package org.chaiware.acommander.actions;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void convertGraphicsFilesActionHiddenWhenSelectionContainsNonImage() throws Exception {
        Path image = Files.createTempFile(tempDir, "pic", ".png");
        Path text = Files.createTempFile(tempDir, "note", ".txt");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getSelectedItems()).thenReturn(List.of(
                new FileItem(image.toFile()),
                new FileItem(text.toFile())
        ));

        Commander commander = new Commander();
        commander.filesPanesHelper = panesHelper;

        AppRegistry registry = new AppRegistry(configWithConvertActions());
        ActionRegistry actionRegistry = new ActionRegistry(registry, new ActionExecutor(commander, registry));
        ActionContext context = new ActionContext(commander);

        AppAction action = actionRegistry.all().stream()
                .filter(a -> "convertGraphicsFiles".equals(a.id()))
                .findFirst()
                .orElseThrow();

        Assertions.assertThat(action.isEnabled(context)).isFalse();
    }

    @Test
    void convertAudioFilesActionHiddenWhenSelectionContainsNonAudio() throws Exception {
        Path audio = Files.createTempFile(tempDir, "sound", ".wav");
        Path text = Files.createTempFile(tempDir, "note", ".txt");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getSelectedItems()).thenReturn(List.of(
                new FileItem(audio.toFile()),
                new FileItem(text.toFile())
        ));

        Commander commander = new Commander();
        commander.filesPanesHelper = panesHelper;

        AppRegistry registry = new AppRegistry(configWithConvertActions());
        ActionRegistry actionRegistry = new ActionRegistry(registry, new ActionExecutor(commander, registry));
        ActionContext context = new ActionContext(commander);

        AppAction action = actionRegistry.all().stream()
                .filter(a -> "convertAudioFiles".equals(a.id()))
                .findFirst()
                .orElseThrow();

        Assertions.assertThat(action.isEnabled(context)).isFalse();
    }

    private static AppConfig configWithConvertActions() {
        ActionDefinition imageConvert = new ActionDefinition();
        imageConvert.setId("convertGraphicsFiles");
        imageConvert.setLabel("Convert Graphics Files");
        imageConvert.setType("builtin");
        imageConvert.setContexts(List.of("commandPalette"));
        imageConvert.setSelection("any");

        ActionDefinition audioConvert = new ActionDefinition();
        audioConvert.setId("convertAudioFiles");
        audioConvert.setLabel("Convert Audio Files");
        audioConvert.setType("builtin");
        audioConvert.setContexts(List.of("commandPalette"));
        audioConvert.setSelection("any");

        AppConfig config = new AppConfig();
        config.setActions(List.of(imageConvert, audioConvert));
        return config;
    }
}
