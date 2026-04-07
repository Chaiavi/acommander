package org.chaiware.acommander.actions;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.PriorityRuleDefinition;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionPriorityEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsZeroWhenNoPriorityConfigured() {
        ActionPriorityEngine engine = new ActionPriorityEngine();
        ActionDefinition action = new ActionDefinition();
        action.setId("alpha");

        int priority = engine.priority(action, new ActionContext(null));

        Assertions.assertThat(priority).isEqualTo(0);
    }

    @Test
    void appliesBaseAndMatchingMp3Rule() throws Exception {
        ActionPriorityEngine engine = new ActionPriorityEngine();
        ActionDefinition action = new ActionDefinition();
        action.setId("editAudioMetadata");
        action.setPriority(10);

        PriorityRuleDefinition rule = new PriorityRuleDefinition();
        rule.setScore(120);
        rule.setExtensions(List.of("mp3"));
        rule.setExtensionMatch("all");
        action.setPriorityRules(List.of(rule));

        Path mp3 = Files.createTempFile(tempDir, "track", ".mp3");
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getSelectedItems()).thenReturn(List.of(new FileItem(mp3.toFile())));

        Commander commander = new Commander();
        commander.filesPanesHelper = panesHelper;

        int priority = engine.priority(action, new ActionContext(commander));

        Assertions.assertThat(priority).isEqualTo(130);
    }

    @Test
    void doesNotApplyMp3AllRuleWhenSelectionContainsNonMp3() throws Exception {
        ActionPriorityEngine engine = new ActionPriorityEngine();
        ActionDefinition action = new ActionDefinition();
        action.setId("editAudioMetadata");

        PriorityRuleDefinition rule = new PriorityRuleDefinition();
        rule.setScore(120);
        rule.setExtensions(List.of("mp3"));
        rule.setExtensionMatch("all");
        action.setPriorityRules(List.of(rule));

        Path mp3 = Files.createTempFile(tempDir, "track", ".mp3");
        Path wav = Files.createTempFile(tempDir, "track2", ".wav");
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getSelectedItems()).thenReturn(List.of(
                new FileItem(mp3.toFile()),
                new FileItem(wav.toFile())
        ));

        Commander commander = new Commander();
        commander.filesPanesHelper = panesHelper;

        int priority = engine.priority(action, new ActionContext(commander));

        Assertions.assertThat(priority).isEqualTo(0);
    }

    @Test
    void appliesClipboardRuleWhenClipboardHasEntries() {
        ActionPriorityEngine engine = new ActionPriorityEngine();
        ActionDefinition action = new ActionDefinition();
        action.setId("pasteSelection");

        PriorityRuleDefinition rule = new PriorityRuleDefinition();
        rule.setScore(80);
        rule.setClipboardNotEmpty(true);
        action.setPriorityRules(List.of(rule));

        Commander commander = mock(Commander.class);
        when(commander.hasClipboardTransferEntries()).thenReturn(true);

        int priority = engine.priority(action, new ActionContext(commander));

        Assertions.assertThat(priority).isEqualTo(80);
    }
}
