package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudioMetadataSupportTest {

    @Test
    void parsesQueryOutputOfTheBundledId3() {
        String output = "My Title\tSome Artist\t<empty>\t3\t2020\t<empty>\t<empty>\r\n";

        assertThat(AudioMetadataSupport.parseQuery(output)).containsExactly(
                Map.entry("title", "My Title"), Map.entry("artist", "Some Artist"), Map.entry("album", ""),
                Map.entry("track", "3"), Map.entry("year", "2020"), Map.entry("genre", ""), Map.entry("comment", ""));
    }

    @Test
    void unexpectedOutputGivesEveryFieldEmpty() {
        assertThat(AudioMetadataSupport.parseQuery("id3: cannot open file")).hasSize(7).containsValues("").doesNotContainValue("id3: cannot open file");
    }
}
