package org.chaiware.acommander.dialog;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.stage.Window;
import org.chaiware.acommander.dialog.MetadataFormDialog.Field;
import org.chaiware.acommander.helpers.AudioMetadataSupport;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/** Edits an mp3's ID3 tags with id3.exe. */
public final class AudioMetadataDialog {
    private static final List<Field> FIELDS = List.of(
            new Field("title", "Title", "-t", "The song title."),
            new Field("artist", "Artist", "-a", "The performing artist."),
            new Field("album", "Album", "-l", "The album the song is on."),
            new Field("track", "Track", "-n", "The track number, e.g. 3 or 3/12."),
            new Field("year", "Year", "-y", "The release year."),
            new Field("genre", "Genre", "-g", "The genre name or ID3 genre number."),
            new Field("comment", "Comment", "-c", "A free-text comment."));

    private enum TagVersion {
        ID3V2("ID3v2 (Recommended)", "-2"),
        ID3V1("ID3v1", "-1"),
        ID3V1_V2("ID3v1 + ID3v2", "-3");

        private final String label;
        private final String flag;

        TagVersion(String label, String flag) {
            this.label = label;
            this.flag = flag;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private AudioMetadataDialog() {
    }

    /** Shows the editor until closed; true when a save changed the file. */
    public static boolean show(Window owner, String themeClass, File mp3) {
        Charset charset = AudioMetadataSupport.nativeCharset();
        ComboBox<TagVersion> version = OptionsDialog.tip(new ComboBox<>(FXCollections.observableArrayList(TagVersion.values())),
                "Which ID3 tag Save writes the changes to.");
        version.setValue(TagVersion.ID3V2);
        return MetadataFormDialog.show(owner, themeClass, "Edit Audio Metadata", mp3, "id3.exe", FIELDS,
                Map.of("Tag Version", version), new MetadataFormDialog.Tool() {
                    @Override
                    public Map<String, String> read() throws Exception {
                        return AudioMetadataSupport.read(mp3, charset);
                    }

                    @Override
                    public List<String> writeCommand(List<String> changes, boolean preserveTime) {
                        return AudioMetadataSupport.writeCommand(version.getValue().flag, preserveTime, changes, mp3);
                    }

                    @Override
                    public void write(List<String> command) throws Exception {
                        AudioMetadataSupport.write(command, charset);
                    }
                });
    }
}
