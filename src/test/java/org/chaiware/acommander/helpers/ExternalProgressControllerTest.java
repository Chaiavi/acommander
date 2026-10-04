package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalProgressControllerTest {

    @Test
    void toolNameIsTheExeFileName() {
        assertThat(ExternalProgressController.toolName(List.of("C:\\apps\\copy\\fcp.exe", "/cmd=diff"))).isEqualTo("fcp.exe");
        assertThat(ExternalProgressController.toolName(List.of())).isEqualTo("external command");
        assertThat(ExternalProgressController.toolName(null)).isEqualTo("external command");
    }

    @Test
    void knownNonZeroExitsAreNotFailures() {
        assertThat(ExternalProgressController.isFailedExit(List.of("C:\\x\\ExamDiff.exe", "a", "b"), 27)).isFalse();
        assertThat(ExternalProgressController.isFailedExit(List.of("explorer.exe", "C:\\"), 1)).isFalse();
        assertThat(ExternalProgressController.isFailedExit(List.of("explorer.exe", "C:\\"), 2)).isTrue();
        assertThat(ExternalProgressController.isFailedExit(List.of("7z.exe"), 1)).isTrue();
        assertThat(ExternalProgressController.isFailedExit(List.of("7z.exe"), 0)).isFalse();
    }
}
