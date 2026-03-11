package org.chaiware.acommander.commands;

public class ExternalCommandException extends RuntimeException {
    private final int exitCode;
    private final String command;
    private final String outputTail;

    public ExternalCommandException(int exitCode, String command, String outputTail) {
        super(buildMessage(exitCode, command, outputTail));
        this.exitCode = exitCode;
        this.command = command;
        this.outputTail = outputTail;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getCommand() {
        return command;
    }

    public String getOutputTail() {
        return outputTail;
    }

    private static String buildMessage(int exitCode, String command, String outputTail) {
        StringBuilder message = new StringBuilder();
        message.append("External command failed with exit code ").append(exitCode).append(": ").append(command);
        if (outputTail != null && !outputTail.isBlank()) {
            message.append(System.lineSeparator()).append("Output: ").append(outputTail);
        }
        return message.toString();
    }
}
