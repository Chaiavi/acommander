package org.chaiware.acommander.helpers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds the prefilled GitHub "new issue" URL for the Report Bug dialog. */
public final class BugReportUrl {
    private static final String NEW_ISSUE_URL = "https://github.com/Chaiavi/acommander/issues/new";
    /** GitHub rejects longer new-issue URLs. */
    static final int MAX_URL_LENGTH = 8000;
    static final String CUT_NOTE = "\n\n[Cut to fit the link. Please paste the rest here.]";

    private BugReportUrl() {
    }

    public static String build(String type, String title, String steps, String expected, String actual, String version) {
        String details = "Steps to reproduce:\n" + safe(steps)
                + "\n\nExpected:\n" + safe(expected)
                + "\n\nActual:\n" + safe(actual);
        String footer = "\n\nApp version: " + safe(version);
        String label = label(type);
        String head = NEW_ISSUE_URL + "?title=" + encode(titlePrefix(type) + safe(title)) + "&body=";
        // GitHub applies the label only for users with triage rights; others get an unlabeled issue.
        String tail = label.isEmpty() ? "" : "&labels=" + label;
        int budget = MAX_URL_LENGTH - head.length() - tail.length();

        String body = encode(details + footer);
        for (int keep = details.length(); body.length() > budget && keep > 0; ) {
            keep = keep * 9 / 10;
            body = encode(details.substring(0, keep) + CUT_NOTE + footer);
        }
        return head + body + tail;
    }

    static String titlePrefix(String type) {
        return switch (safe(type)) {
            case "Feature Request" -> "Feature: ";
            case "Question" -> "Question: ";
            case "Other" -> "Other: ";
            default -> "Bug: ";
        };
    }

    static String label(String type) {
        return switch (safe(type)) {
            case "Feature Request" -> "enhancement";
            case "Question" -> "question";
            case "Other" -> "";
            default -> "bug";
        };
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
