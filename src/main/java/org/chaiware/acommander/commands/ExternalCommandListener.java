package org.chaiware.acommander.commands;

import java.util.List;

public interface ExternalCommandListener {
    void onCommandStarted(List<String> command);
    void onCommandFinished(List<String> command, int exitCode, Throwable error);
    /** Work nobody waits on failed; show it to the user. */
    void onFailure(String title, Throwable error);
}
