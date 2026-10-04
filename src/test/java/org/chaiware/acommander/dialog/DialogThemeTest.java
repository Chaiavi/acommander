package org.chaiware.acommander.dialog;

import org.chaiware.acommander.dialog.DialogTheme.ThemeMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogThemeTest {

    @Test
    void onlyDarkIsDark() {
        assertThat(ThemeMode.from("DARK")).isEqualTo(ThemeMode.DARK);
        assertThat(ThemeMode.from("light")).isEqualTo(ThemeMode.REGULAR);
        assertThat(ThemeMode.from("")).isEqualTo(ThemeMode.REGULAR);
        assertThat(ThemeMode.from(null)).isEqualTo(ThemeMode.REGULAR);
        assertThat(ThemeMode.from("purple")).isEqualTo(ThemeMode.REGULAR);
    }

    @Test
    void settingsValueRoundTrips() {
        for (ThemeMode mode : ThemeMode.values()) {
            assertThat(ThemeMode.from(mode.configValue)).isEqualTo(mode);
        }
    }
}
