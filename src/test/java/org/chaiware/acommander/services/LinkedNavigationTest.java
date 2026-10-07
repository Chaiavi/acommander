package org.chaiware.acommander.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedNavigationTest {

    @TempDir
    Path dir;

    @Test
    void followsIntoTheSameNamedSubfolder() throws IOException {
        Path sub = Files.createDirectory(dir.resolve("sub"));

        assertThat(LinkedNavigation.target(dir.toString(), "sub")).contains(sub.toString());
    }

    @Test
    void staysWhenTheSubfolderIsMissingOrAFile() throws IOException {
        Files.createFile(dir.resolve("file"));

        assertThat(LinkedNavigation.target(dir.toString(), "missing")).isEmpty();
        assertThat(LinkedNavigation.target(dir.toString(), "file")).isEmpty();
    }

    @Test
    void goesUpToTheParent() {
        assertThat(LinkedNavigation.target(dir.toString(), null)).contains(dir.getParent().toString());
    }

    @Test
    void staysAtADriveRoot() {
        String root = File.listRoots()[0].getPath();

        assertThat(LinkedNavigation.target(root, null)).isEmpty();
    }
}
