package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionTest {

    @Test
    void matchesAppVersionInBuildGradle() {
        String buildVersion = System.getProperty("acommander.appVersion");

        assertThat(buildVersion).as("build.gradle passes appVersion to the test JVM").isNotBlank();
        assertThat(AppVersion.current()).isEqualTo(buildVersion);
    }
}
