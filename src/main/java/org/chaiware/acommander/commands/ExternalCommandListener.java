package org.chaiware.acommander.commands;

import java.util.List;

public interface ExternalCommandListener {
    void onCommandStarted(List<String> command);
    void onCommandFinished(List<String> command, int exitCode, Throwable error);
}
