package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageMetadataSupportTest {

    @Test
    void parsesPrintAllOutputOfTheBundledExiv2() {
        String output = """
                Exif.Image.Make                              Ascii       6  Canon
                Exif.Image.Artist                            Ascii       9  Jane Doe
                Iptc.Application2.Keywords                   String      6  sunset
                Xmp.dc.title                                 LangAlt     1  lang="x-default" Hello World
                Exif.Photo.UserComment                       Comment     0
                """;

        assertThat(ImageMetadataSupport.parsePrintAll(output)).containsExactly(
                new ImageMetadataSupport.Exiv2Entry("Exif.Image.Make", "Ascii", "Canon"),
                new ImageMetadataSupport.Exiv2Entry("Exif.Image.Artist", "Ascii", "Jane Doe"),
                new ImageMetadataSupport.Exiv2Entry("Iptc.Application2.Keywords", "String", "sunset"),
                new ImageMetadataSupport.Exiv2Entry("Xmp.dc.title", "LangAlt", "Hello World"));
    }

    @Test
    void groupsKeysByFamilyAndSection() {
        assertThat(ImageMetadataSupport.groupName("Exif.Image.Make")).isEqualTo("EXIF - Image");
        assertThat(ImageMetadataSupport.groupName("Xmp.dc.title")).isEqualTo("XMP - dc");
        assertThat(ImageMetadataSupport.groupName("Iptc.Application2.Keywords")).isEqualTo("IPTC - Application2");
        assertThat(ImageMetadataSupport.groupName("Thumbnail.Image")).isEqualTo("Thumbnail");
        assertThat(ImageMetadataSupport.groupName("Unknown")).isEqualTo("Other");
    }
}
