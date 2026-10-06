package org.chaiware.acommander.commands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExternalToolRunnerTest {

    private final ExternalCommandListener listener = mock(ExternalCommandListener.class);
    private final ExternalToolRunner runner = new ExternalToolRunner(() -> {});

    ExternalToolRunnerTest() {
        runner.setListener(listener);
    }

    @Test
    void failureOfUnwatchedWorkReachesTheUserWithItsCause() {
        CompletableFuture<Void> work = new CompletableFuture<>();
        runner.reportFailure(work.thenRun(() -> {}), "Copy");

        IllegalStateException cause = new IllegalStateException("disk full");
        work.completeExceptionally(cause);

        verify(listener).onFailure("Copy", cause);
    }

    @Test
    void successIsNotReported() {
        runner.reportFailure(CompletableFuture.completedFuture(null), "Copy");

        verify(listener, never()).onFailure(anyString(), any());
    }

    @Test
    void workTheUserStoppedIsNotReported() {
        CompletableFuture<Void> work = new CompletableFuture<>();
        runner.reportFailure(work, "Copy");

        work.completeExceptionally(new java.util.concurrent.CompletionException(new OperationStoppedException()));

        verify(listener, never()).onFailure(anyString(), any());
    }

    @Test
    void aDifferentToolFailingAfterStopIsStillReported() {
        Operation.stopAll();
        CompletableFuture<Void> other = new CompletableFuture<>();
        runner.reportFailure(other, "Checksum");

        ExternalCommandException failure = new ExternalCommandException(2, "rhash.exe", "");
        other.completeExceptionally(failure);

        verify(listener).onFailure("Checksum", failure);
    }

    @Test
    @org.junit.jupiter.api.Timeout(20)
    void stopKillsTheRunningToolAndItEndsAsStopped() throws Exception {
        CompletableFuture<List<String>> run = runner.runExecutable(List.of("ping", "-n", "30", "127.0.0.1"), false);
        Thread.sleep(500);

        Operation.stopAll();

        assertThatThrownBy(run::join).hasCauseInstanceOf(OperationStoppedException.class);
    }

    @Test
    void aStoppedOperationStartsNoFurtherTool(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path marker = dir.resolve("ran.txt");
        Operation operation = Operation.start();
        try {
            assertThatThrownBy(() -> operation.run(() -> {
                Operation.stopAll();
                return runner.runExecutable(List.of("cmd.exe", "/c", "echo x>" + marker), false).join();
            })).hasCauseInstanceOf(OperationStoppedException.class);
        } finally {
            operation.finish();
        }
        assertThat(marker).doesNotExist();
    }

    @Test
    void operationsStartedAfterStopRunNormally() throws Exception {
        Operation.stopAll();

        assertThat(runner.runExecutable(List.of("cmd.exe", "/c", "exit 0"), false).join()).isEmpty();
    }
}
