package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BugReportUrlTest {

    @Test
    void bugReportGetsBugLabelAndPrefix() {
        String url = BugReportUrl.build("Bug Report", "Copy fails", "1. copy", "copied", "error", "4.0");
        assertThat(url)
                .startsWith("https://github.com/Chaiavi/acommander/issues/new?title=Bug%3A%20Copy%20fails&body=")
                .endsWith("&labels=bug")
                .contains("App%20version%3A%204.0");
    }

    @Test
    void featureRequestGetsEnhancementLabel() {
        assertThat(BugReportUrl.build("Feature Request", "Tabs", null, null, null, "4.0"))
                .contains("title=Feature%3A%20Tabs")
                .endsWith("&labels=enhancement");
    }

    @Test
    void questionGetsQuestionLabel() {
        assertThat(BugReportUrl.build("Question", "How?", "", "", "", "4.0")).endsWith("&labels=question");
    }

    @Test
    void otherHasNoLabel() {
        assertThat(BugReportUrl.build("Other", "Hi", "", "", "", "4.0")).doesNotContain("labels=");
    }

    @Test
    void nullTypeDefaultsToBug() {
        assertThat(BugReportUrl.build(null, null, null, null, null, null))
                .contains("title=Bug%3A%20")
                .endsWith("&labels=bug");
    }
}
