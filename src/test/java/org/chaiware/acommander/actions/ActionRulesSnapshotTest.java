package org.chaiware.acommander.actions;

import org.chaiware.acommander.Commander;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * Locks every apps.json action's FTP gate, read-only gate and palette enablement. On a mismatch the actual table is
 * written to build/action-rules-snapshot.actual.txt.
 */
class ActionRulesSnapshotTest {
    private static final Path SNAPSHOT = Path.of("src", "test", "resources", "action-rules-snapshot.txt");
    private static final Path ACTUAL = Path.of("build", "action-rules-snapshot.actual.txt");
    private static final Set<String> GATE_METHODS = Set.of("showError", "showReadOnlyLocationWarning");

    @TempDir
    Path tempDir;

    @Test
    void actionRulesMatchSnapshot() throws IOException {
        AppConfig config = new AppConfigLoader().load(Path.of("config", "apps.json"));
        Map<String, List<FileItem>> samples = samples();
        StringBuilder actual = new StringBuilder("# id | ftp | writes | palette enabled for: ")
                .append(String.join(" ", samples.keySet()))
                .append('\n');
        List<ActionDefinition> actions = config.getActions().stream()
                .sorted(Comparator.comparing(ActionDefinition::getId))
                .toList();
        for (ActionDefinition action : actions) {
            actual.append(action.getId())
                    .append(" | ftp=").append(blocked(config, action, mock(FtpFileSystem.class), writable()) ? "no" : "yes")
                    .append(" | writes=").append(writes(config, action))
                    .append(" | ").append(paletteRow(config, action, samples))
                    .append('\n');
        }

        String expected = Files.exists(SNAPSHOT) ? Files.readString(SNAPSHOT).replace("\r\n", "\n") : "";
        if (!actual.toString().equals(expected)) {
            Files.createDirectories(ACTUAL.getParent());
            Files.writeString(ACTUAL, actual);
        }
        assertThat(actual.toString()).as("action rules changed; see " + ACTUAL.toAbsolutePath()).isEqualTo(expected);
    }

    private String writes(AppConfig config, ActionDefinition action) {
        boolean source = blocked(config, action, readOnly(), writable());
        boolean target = blocked(config, action, writable(), readOnly());
        if (source && target) return "both";
        if (source) return "source";
        if (target) return "target";
        return "none";
    }

    private boolean blocked(AppConfig config, ActionDefinition action, VFileSystem focused, VFileSystem unfocused) {
        Commander commander = mock(Commander.class);
        FilesPanesHelper panes = mock(FilesPanesHelper.class);
        when(panes.getFocusedFileSystem()).thenReturn(focused);
        when(panes.getUnfocusedFileSystem()).thenReturn(unfocused);
        commander.filesPanesHelper = panes;
        try {
            new ActionExecutor(commander, new AppRegistry(config)).execute(action);
        } catch (RuntimeException ignored) {
            // The action itself runs against mocks; only the gates in front of it matter here.
        }
        return mockingDetails(commander).getInvocations().stream()
                .anyMatch(invocation -> GATE_METHODS.contains(invocation.getMethod().getName()));
    }

    private String paletteRow(AppConfig config, ActionDefinition action, Map<String, List<FileItem>> samples) {
        StringBuilder row = new StringBuilder("palette=");
        for (List<FileItem> selection : samples.values()) {
            FilesPanesHelper panes = mock(FilesPanesHelper.class);
            when(panes.getSelectedItems()).thenReturn(selection);
            when(panes.getFocusedFileSystem()).thenReturn(writable());
            when(panes.getUnfocusedFileSystem()).thenReturn(writable());
            Commander commander = new Commander();
            commander.filesPanesHelper = panes;
            AppRegistry registry = new AppRegistry(config);
            AppAction paletteAction = new ActionRegistry(registry, new ActionExecutor(commander, registry)).all().stream()
                    .filter(candidate -> candidate.id().equals(action.getId()))
                    .findFirst()
                    .orElse(null);
            if (paletteAction == null) {
                return "palette=n/a";
            }
            try {
                row.append(paletteAction.isEnabled(new ActionContext(commander)) ? '+' : '-');
            } catch (RuntimeException e) {
                row.append('x');
            }
        }
        return row.toString();
    }

    private Map<String, List<FileItem>> samples() throws IOException {
        Map<String, List<FileItem>> samples = new LinkedHashMap<>();
        samples.put("none", List.of());
        samples.put("dir", List.of(new FileItem(Files.createDirectory(tempDir.resolve("folder")))));
        for (String ext : List.of("txt", "png", "jpg", "wav", "mp3", "mp4", "pdf", "zip", "exe")) {
            samples.put(ext, List.of(file("one." + ext)));
        }
        samples.put("2txt", List.of(file("a.txt"), file("b.txt")));
        samples.put("2pdf", List.of(file("a.pdf"), file("b.pdf")));
        samples.put("png+txt", List.of(file("one.png"), file("one.txt")));
        return samples;
    }

    private FileItem file(String name) throws IOException {
        Path path = tempDir.resolve(name);
        if (!Files.exists(path)) {
            Files.writeString(path, "sample");
        }
        return new FileItem(path);
    }

    private static VFileSystem writable() {
        return mock(VFileSystem.class);
    }

    private static VFileSystem readOnly() {
        VFileSystem fs = mock(VFileSystem.class);
        when(fs.isReadOnly()).thenReturn(true);
        return fs;
    }
}
