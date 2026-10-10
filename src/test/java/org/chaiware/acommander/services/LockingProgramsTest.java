package org.chaiware.acommander.services;

import org.chaiware.acommander.services.LockingPrograms.Locker;
import org.chaiware.acommander.tools.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LockingProgramsTest {

    @Test
    void parseReadsTheTableRowsAndSkipsTheHeader() {
        List<String> output = List.of("PID\tUser\tProcess", "3404\tJohn Smith\tCode.exe", "17328\tjohn\tMy App.exe");

        assertThat(LockingPrograms.parse(output)).containsExactly(
                new Locker(3404, "John Smith", "Code.exe"),
                new Locker(17328, "john", "My App.exe"));
    }

    @Test
    void parseFindsNothingWhenNoProgramHoldsTheFiles() {
        assertThat(LockingPrograms.parse(List.of("No processes found locking the file(s)."))).isEmpty();
    }

    @Test
    void endStopsAProgramAndSkipsOnesAlreadyGone() throws Exception {
        Process ping = ProcessRunner.of("ping", "-n", "60", "127.0.0.1").launch();
        try {
            List<Locker> running = LockingPrograms.end(List.of(
                    new Locker(ping.pid(), "me", "PING.EXE"),
                    new Locker(Long.MAX_VALUE, "me", "gone.exe")));

            assertThat(running).isEmpty();
            assertThat(ping.waitFor(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            ping.destroyForcibly();
        }
    }

    @Test
    void endNeverStopsACommanderItself() {
        Locker self = new Locker(ProcessHandle.current().pid(), "me", "java.exe");

        assertThat(LockingPrograms.end(List.of(self))).containsExactly(self);
    }

    @Test
    void endLeavesAProgramWhosePidNowBelongsToAnotherOne() throws Exception {
        Process ping = ProcessRunner.of("ping", "-n", "60", "127.0.0.1").launch();
        try {
            assertThat(LockingPrograms.end(List.of(new Locker(ping.pid(), "me", "closed.exe")))).isEmpty();
            assertThat(ping.isAlive()).isTrue();
        } finally {
            ping.destroyForcibly();
        }
    }
}
