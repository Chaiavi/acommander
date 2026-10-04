package org.chaiware.acommander.helpers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds the prefilled GitHub "new issue" URL for the Report Bug dialog. */
public final class BugReportUrl {
    private static final String NEW_ISSUE_URL = "https://github.com/Chaiavi/acommander/issues/new";

    private BugReportUrl() {
    }

    public static String build(String type, String title, String steps, String expected, String actual, String version) {
        String body = "Steps to reproduce:\n" + safe(steps)
                + "\n\nExpected:\n" + safe(expected)
                + "\n\nActual:\n" + safe(actual)
                + "\n\nApp version: " + safe(version);
        String url = NEW_ISSUE_URL + "?title=" + encode(titlePrefix(type) + safe(title)) + "&body=" + encode(body);
        String label = label(type);
        // GitHub applies the label only for users with triage rights; others get an unlabeled issue.
        return label.isEmpty() ? url : url + "&labels=" + label;
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
