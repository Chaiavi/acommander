package org.chaiware.acommander4j;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

public class HelloWorldTest {

    @Test
    void testGetElapsedTime() throws InterruptedException {
        Instant start = Instant.now();
        Thread.sleep(50);
        String elapsed = HelloWorld.getElapsedTime(start);
        Assertions.assertTrue(elapsed.startsWith("0."));

        Thread.sleep(1000);
        elapsed = HelloWorld.getElapsedTime(start);
        Assertions.assertTrue(elapsed.startsWith("1."));

        Thread.sleep(1000);
        elapsed = HelloWorld.getElapsedTime(start);
        Assertions.assertTrue(elapsed.startsWith("2."));
    }
}
