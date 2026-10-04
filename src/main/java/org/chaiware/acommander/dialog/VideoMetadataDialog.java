package org.chaiware.acommander.dialog;

import javafx.stage.Window;
import org.chaiware.acommander.dialog.MetadataFormDialog.Field;
import org.chaiware.acommander.helpers.VideoMetadataSupport;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Edits the tags of an MP4 / M4V / 3GP with AtomicParsley. */
public final class VideoMetadataDialog {
    private static final List<Field> FIELDS = List.of(
            new Field("title", "Title", "--title", "The video title."),
            new Field("artist", "Artist", "--artist", "The artist or creator."),
            new Field("album", "Album", "--album", "The album or series the video belongs to."),
            new Field("genre", "Genre", "--genre", "The genre name."),
            new Field("year", "Year", "--year", "The release year or date."),
            new Field("tracknum", "Track", "--tracknum", "The track number, e.g. 3 or 3/12."),
            new Field("disk", "Disk", "--disk", "The disk number, e.g. 1 or 1/2."),
            new Field("comment", "Comment", "--comment", "A free-text comment."),
            new Field("composer", "Composer", "--composer", "The composer or writer."),
            new Field("description", "Description", "--description", "A short description of the video."));

    private VideoMetadataDialog() {
    }

    /** Shows the editor until closed; true when a save changed the file. */
    public static boolean show(Window owner, String themeClass, File video) {
        return MetadataFormDialog.show(owner, themeClass, "Edit Video Metadata", video, "AtomicParsley", FIELDS,
                Map.of(), new MetadataFormDialog.Tool() {
                    @Override
                    public Map<String, String> read() throws Exception {
                        return VideoMetadataSupport.read(video);
                    }

                    @Override
                    public List<String> writeCommand(List<String> changes, boolean preserveTime) {
                        return VideoMetadataSupport.writeCommand(video, changes, preserveTime);
                    }

                    @Override
                    public void write(List<String> command) throws Exception {
                        VideoMetadataSupport.write(video, command);
                    }
                });
    }
}
