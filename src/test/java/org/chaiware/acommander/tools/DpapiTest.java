package org.chaiware.acommander.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DpapiTest {

    @Test
    void roundTripsAnyPasswordAndEmptiesWhatItCannotDecrypt() throws Exception {
        Dpapi dpapi = new Dpapi();
        List<String> passwords = List.of("p\"a\\ss'word", "סיסמה ✓", "x");

        List<String> ciphers = dpapi.protect(passwords);

        assertThat(ciphers).hasSize(3).noneMatch(cipher -> cipher.contains("ss'word"));
        assertThat(dpapi.unprotect(ciphers)).isEqualTo(passwords);
        assertThat(dpapi.unprotect(List.of("bm90IGVuY3J5cHRlZA==", ciphers.get(2)))).containsExactly("", "x");
    }
}
