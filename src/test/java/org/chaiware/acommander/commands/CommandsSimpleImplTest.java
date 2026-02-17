package org.chaiware.acommander.commands;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommandsSimpleImplTest {

    @TempDir
    Path tempDir;

    @Test
    void copyCopiesFileToTargetFolder() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path sourceFile = sourceDir.resolve("data.txt");
        Files.writeString(sourceFile, "hello");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.copy(new FileItem(sourceFile.toFile()), targetDir.toString());

        Path copied = targetDir.resolve("data.txt");
        Assertions.assertThat(copied).exists();
        Assertions.assertThat(Files.readString(copied)).isEqualTo("hello");
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    void copyCopiesDirectoryRecursively() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path childDir = Files.createDirectory(sourceDir.resolve("child"));
        Files.writeString(childDir.resolve("data.txt"), "nested");

        Path targetDir = tempDir.resolve("target");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.copy(new FileItem(sourceDir.toFile()), targetDir.toString());

        Assertions.assertThat(targetDir.resolve("child").resolve("data.txt")).exists();
        Assertions.assertThat(Files.readString(targetDir.resolve("child").resolve("data.txt"))).isEqualTo("nested");
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    void moveMovesFileToTargetFolder() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path sourceFile = sourceDir.resolve("move.txt");
        Files.writeString(sourceFile, "move");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.move(new FileItem(sourceFile.toFile()), targetDir.toString());

        Assertions.assertThat(sourceFile).doesNotExist();
        Assertions.assertThat(targetDir.resolve("move.txt")).exists();
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    void renameRenamesSingleFile() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path sourceFile = sourceDir.resolve("old.txt");
        Files.writeString(sourceFile, "rename");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.rename(List.of(new FileItem(sourceFile.toFile())), "new.txt");

        Assertions.assertThat(sourceDir.resolve("old.txt")).doesNotExist();
        Assertions.assertThat(sourceDir.resolve("new.txt")).exists();
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    void mkdirCreatesDirectory() throws Exception {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.mkdir(tempDir.toString(), "created");

        Assertions.assertThat(tempDir.resolve("created")).exists().isDirectory();
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    void mkFileCreatesFile() throws Exception {
        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        CommandsSimpleImpl commands = new CommandsSimpleImpl(panesHelper);

        commands.mkFile(tempDir.toString(), "file.txt");

        Assertions.assertThat(tempDir.resolve("file.txt")).exists();
        verify(panesHelper).refreshFileListViews();
    }
}
