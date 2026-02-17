package org.chaiware.acommander.tools;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCommandBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildCommandExpandsPlaceholdersAndSelectedFilesToken() throws Exception {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        Path selected = Files.createTempFile(tempDir, "file", ".txt");
        FileItem selectedItem = new FileItem(selected.toFile());

        when(panesHelper.getSelectedItems()).thenReturn(List.of(selectedItem));
        when(panesHelper.getFocusedPath()).thenReturn("C:\\focused");
        when(panesHelper.getUnfocusedPath()).thenReturn("C:\\target");

        List<String> command = ToolCommandBuilder.buildCommand(
                "tool.exe",
                List.of("--in", "${focusedPath}", "--target", "${targetFolder}", "${selectedFiles}", "--extra", "${extra}"),
                panesHelper,
                Map.of("${extra}", "value"),
                null
        );

        Assertions.assertThat(command)
                .containsSubsequence("--in", "C:\\focused", "--target", "C:\\target");
        Assertions.assertThat(command)
                .contains(selected.toString());
        Assertions.assertThat(command)
                .contains("--extra", "value");
    }

    @Test
    void buildCommandOmitsParentFolderFromSelectedFiles() throws Exception {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        Path selected = Files.createTempFile(tempDir, "file", ".txt");
        FileItem parent = new FileItem(tempDir.toFile(), "..");
        FileItem selectedItem = new FileItem(selected.toFile());

        when(panesHelper.getSelectedItems()).thenReturn(List.of(parent, selectedItem));
        when(panesHelper.getFocusedPath()).thenReturn("C:\\focused");
        when(panesHelper.getUnfocusedPath()).thenReturn("C:\\target");

        List<String> command = ToolCommandBuilder.buildCommand(
                "tool.exe",
                List.of("${selectedFiles}"),
                panesHelper
        );

        Assertions.assertThat(command)
                .containsExactly(
                        Path.of(System.getProperty("user.dir"), "tool.exe").toString(),
                        selected.toString()
                );
    }

    @Test
    void resolveTemplateQuotesSelectedFilesJoined() throws Exception {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        Path first = Files.createTempFile(tempDir, "file one", ".txt");
        Path second = Files.createTempFile(tempDir, "file two", ".txt");

        when(panesHelper.getSelectedItems()).thenReturn(List.of(
                new FileItem(first.toFile()),
                new FileItem(second.toFile())
        ));
        when(panesHelper.getFocusedPath()).thenReturn("C:\\focused");
        when(panesHelper.getUnfocusedPath()).thenReturn("C:\\target");

        String rendered = ToolCommandBuilder.resolveTemplate(
                "files=${selectedFilesJoined}",
                panesHelper,
                null,
                null
        );

        Assertions.assertThat(rendered)
                .contains("\"" + first + "\"")
                .contains("\"" + second + "\"");
    }

    @Test
    void buildCommandReturnsEmptyWhenPathBlank() {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);

        List<String> command = ToolCommandBuilder.buildCommand(
                "   ",
                List.of("--flag"),
                panesHelper
        );

        Assertions.assertThat(command).isEmpty();
    }
}
