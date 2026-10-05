package org.chaiware.acommander.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    void returnsExitCode() throws Exception {
        assertThat(ProcessRunner.of("cmd.exe", "/c", "exit 3").run().exitCode()).isEqualTo(3);
    }

    @Test
    void keepsStdoutAndStderrApart() throws Exception {
        ProcessRunner.Result result = ProcessRunner.of("cmd.exe", "/c", "echo out& echo err 1>&2").run();

        assertThat(result.stdout()).containsExactly("out");
        assertThat(result.stderr()).containsExactly("err ");
        assertThat(result.stdoutText()).isEqualTo("out\n");
    }

    @Test
    void mergesStderrIntoStdout() throws Exception {
        ProcessRunner.Result result = ProcessRunner.of("cmd.exe", "/c", "echo out& echo err 1>&2").mergeStderr().run();

        assertThat(result.stdout()).containsExactlyInAnyOrder("out", "err ");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    @Timeout(30)
    void largeStderrDoesNotHang() throws Exception {
        ProcessRunner.Result result = ProcessRunner.of("cmd.exe", "/c", "for /L %i in (1,1,5000) do @echo line %i 1>&2").run();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.stderr()).hasSize(5000);
    }

    @Test
    void writesStdin() throws Exception {
        assertThat(ProcessRunner.of("findstr", "x").stdin("ax\nb\n").run().stdout()).containsExactly("ax");
    }

    @Test
    void tracksProcessOnlyWhileRunning() throws Exception {
        Set<Process> tracker = ConcurrentHashMap.newKeySet();

        ProcessRunner.of("cmd.exe", "/c", "exit 0").trackIn(tracker).run();

        assertThat(tracker).isEmpty();
    }
}
