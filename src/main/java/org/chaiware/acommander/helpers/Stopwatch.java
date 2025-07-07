package org.chaiware.acommander.helpers;

/** Simplest Stopwatch, it is created started, and toString shows the current elapsed time */
public class Stopwatch {
    private final long startTime;

    public Stopwatch() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        long ms = System.currentTimeMillis() - startTime;

        if (ms < 1000) {
            return ms + " ms";
        } else if (ms < 60000) {
            return String.format("%.2f s", ms / 1000.0);
        } else if (ms < 3600000) {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d:%02d min", minutes, seconds);
        } else {
            long hours = ms / 3600000;
            long minutes = (ms % 3600000) / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
