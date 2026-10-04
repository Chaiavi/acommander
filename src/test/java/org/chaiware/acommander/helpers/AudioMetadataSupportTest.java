package org.chaiware.acommander.helpers;

import org.chaiware.acommander.tools.BundledTool;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test
    void writeCommandPutsVersionAndKeepTimeBeforeTheChangesAndTheFileLast() {
        File mp3 = new File("C:\\music\\song.mp3");

        assertThat(AudioMetadataSupport.writeCommand("-2", true, List.of("-t", "New Title"), mp3))
                .containsExactly(BundledTool.ID3.path().toString(), "-2", "-M", "-t", "New Title", mp3.getAbsolutePath());
        assertThat(AudioMetadataSupport.writeCommand("-1", false, List.of("-y", "2020"), mp3))
                .containsExactly(BundledTool.ID3.path().toString(), "-1", "-y", "2020", mp3.getAbsolutePath());
    }

    @Test
    void findsTheFirstArgumentTheCodePageCannotHold() {
        assertThat(AudioMetadataSupport.firstUnencodable(List.of("-t", "שלום", "x.mp3"), StandardCharsets.US_ASCII)).isEqualTo("שלום");
        assertThat(AudioMetadataSupport.firstUnencodable(List.of("-t", "Hello", "x.mp3"), StandardCharsets.US_ASCII)).isNull();
    }
}
