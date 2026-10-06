package org.chaiware.acommander.commands;

/** Thrown where a stopped operation must not go on: the next item, a fallback, a delete after a copy. */
public class OperationStoppedException extends RuntimeException {
    public OperationStoppedException() {
        super("Stopped");
    }
}
