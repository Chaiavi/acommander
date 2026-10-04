package org.chaiware.acommander.commands;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

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
    void toolStoppedByTheUserIsNotReported() {
        CompletableFuture<Void> work = new CompletableFuture<>();
        runner.reportFailure(work, "Copy");

        runner.stopAll();
        work.completeExceptionally(new ExternalCommandException(1, "fastcopy.exe", ""));

        verify(listener, never()).onFailure(anyString(), any());
    }
}
