package org.chaiware.acommander4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class HelloWorld {
    private static final Logger log = LoggerFactory.getLogger(HelloWorld.class);

    public static void main(String[] args) throws InterruptedException {
        Instant begin = Instant.now();
        System.out.println("Hello world");
        Thread.sleep(2103);
        log.info("Elapsed Time: {}", getElapsedTime(begin));
    }

    /** Returns the time between begin and now <br/>
     * This is a very very basic stopwatch implementation */
    public static String getElapsedTime(Instant begin) {
        return (Duration.between(begin, Instant.now()).toString().replace("PT", "").replace("H", ":").replace("M", ":").replace("S", ""));
    }
}
