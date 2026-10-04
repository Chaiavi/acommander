package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class ReportFailureTest {

    private final ExternalCommandListener listener = mock(ExternalCommandListener.class);
    private final CommandsSimpleImpl commands = new CommandsSimpleImpl(mock(FilesPanesHelper.class));

    ReportFailureTest() {
        commands.setExternalCommandListener(listener);
    }

    @Test
    void failureOfUnwatchedWorkReachesTheUserWithItsCause() {
        CompletableFuture<Void> work = new CompletableFuture<>();
        commands.reportFailure(work.thenRun(() -> {}), "Copy");

        IllegalStateException cause = new IllegalStateException("disk full");
        work.completeExceptionally(cause);

        verify(listener).onFailure("Copy", cause);
    }

    @Test
    void successIsNotReported() {
        commands.reportFailure(CompletableFuture.completedFuture(null), "Copy");

        verify(listener, never()).onFailure(anyString(), any());
    }

    @Test
    void toolStoppedByTheUserIsNotReported() {
        CompletableFuture<Void> work = new CompletableFuture<>();
        commands.reportFailure(work, "Copy");

        commands.stopRunningExternalCommands();
        work.completeExceptionally(new ExternalCommandException(1, "fastcopy.exe", ""));

        verify(listener, never()).onFailure(anyString(), any());
    }
}
