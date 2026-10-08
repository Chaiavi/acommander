package org.chaiware.acommander.tools;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.helpers.ExecutableCompressionSupport;
import org.chaiware.acommander.helpers.ExecutableCompressionSupport.UpxAction;
import org.chaiware.acommander.helpers.ImageConversionSupport;
import org.chaiware.acommander.helpers.ImageMetadataSupport;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer;
import org.chaiware.acommander.services.ImageConversionService;
import org.chaiware.acommander.services.ImageConversionService.ImageCompressionMode;
import org.chaiware.acommander.services.ImageConversionService.ImageConversionRequest;
import org.chaiware.acommander.services.ImageConversionService.ImageResizeMode;
import org.chaiware.acommander.services.PdfExtractOptions;
import org.chaiware.acommander.services.PdfOperations;
import org.chaiware.acommander.tools.BundledToolCommands.ChecksumOptions;
import org.chaiware.acommander.tools.BundledToolCommands.FindInFilesOptions;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs each command-line tool under apps/ the way the app does, on generated files, and reads its output with the
 * app's own parsers. A tool update that changes flags or output fails here before it is pushed. Tools that open a
 * window are in {@link BundledGuiToolContractTest}.
 */
class BundledToolContractTest {
    private static AppRegistry registry;

    @TempDir
    Path dir;

    @BeforeAll
    static void loadActions() throws IOException {
        registry = new AppRegistry(new AppConfigLoader().load(AppPaths.config("apps.json")));
    }

    @Test
    void ripgrepFindsByNameAndByContent() throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "the Needle is here");
        Files.writeString(dir.resolve("other.md"), "nothing");
        String folder = dir.toString();

        List<String> byName = run(BundledToolCommands.findByName(BundledTool.RIPGREP.path(), folder, "*.TXT"));
        List<String> byContent = run(BundledToolCommands.findInFiles(BundledTool.RIPGREP.path(), folder,
                new FindInFilesOptions("needle", true, false, "", false)));

        assertThat(BundledToolCommands.foundFiles(byName, folder)).containsExactly(dir.resolve("notes.txt").toString());
        assertThat(BundledToolCommands.foundFiles(byContent, folder)).containsExactly(dir.resolve("notes.txt").toString());
    }

    @Test
    void rhashPrintsTheChecksum() throws Exception {
        Path file = Files.writeString(dir.resolve("abc.txt"), "abc");

        List<String> output = run(BundledToolCommands.checksum(BundledTool.RHASH.path(), file.toString(),
                ChecksumOptions.of("SHA256", false, false, false), false));

        assertThat(BundledToolCommands.checksumDigest(output))
                .isEqualToIgnoringCase("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void fileNamesTheType() throws Exception {
        Path png = image("picture.png", "png");

        List<String> output = run(BundledToolCommands.analyzeFile(BundledTool.FILE.path(), BundledTool.FILE_MAGIC.path(), png.toString()));

        assertThat(String.join("\n", output)).contains("PNG image data");
    }

    @Test
    void exiv2WritesReadsAndRemovesMetadata() throws Exception {
        Path jpg = image("photo.jpg", "jpg");
        Path commands = Files.writeString(dir.resolve("cmd.txt"), "set Exif.Image.Make Canon\n");
        String exiv2 = BundledTool.EXIV2.path().toString();

        assertThat(ProcessRunner.of(exiv2, "-m", commands.toString(), jpg.toString()).run().exitCode()).isZero();
        assertThat(ImageMetadataSupport.parsePrintAll(ProcessRunner.of(exiv2, "-pa", jpg.toString()).run().stdoutText()))
                .contains(new ImageMetadataSupport.Exiv2Entry("Exif.Image.Make", "Ascii", "Canon"));

        assertThat(ImageMetadataSupport.remove(jpg.toFile())).isTrue();
        assertThat(ImageMetadataSupport.parsePrintAll(ProcessRunner.of(exiv2, "-pa", jpg.toString()).run().stdoutText()))
                .extracting(ImageMetadataSupport.Exiv2Entry::key).doesNotContain("Exif.Image.Make");
    }

    @Test
    void caesiumConvertsToEveryFormatTheAppOffers() throws Exception {
        Path png = image("picture.png", "png");
        for (String format : ImageConversionSupport.targetFormatsForSelection(List.of(new FileItem(png)))) {
            Path out = Files.createDirectory(dir.resolve("out-" + format));
            ImageConversionRequest request = new ImageConversionRequest(format, ImageCompressionMode.QUALITY, 80, null,
                    false, false, ImageResizeMode.NONE, null, false, "", "all");

            List<String> output = new ArrayList<>();
            for (List<String> command : ImageConversionService.buildCommands(BundledTool.CAESIUM.path(), out.toString(),
                    List.of(png.toString()), request)) {
                output.addAll(run(command));
            }

            assertThat(ImageConversionService.failures(output)).as(format).isEmpty();
            assertThat(ImageConversionService.findFirstConverted(List.of("picture.png"), out.toString(), request)).as(format).isNotNull();
        }
    }

    @Test
    void upxCompressesAndDecompresses() throws Exception {
        Path exe = Files.copy(AppPaths.resolve("apps/extract_all/UniExtract/bin/x64/bcm.exe"), dir.resolve("tool.exe"));
        long original = Files.size(exe);

        run(ExecutableCompressionSupport.upxCommand(BundledTool.UPX.path(), UpxAction.GOOD, List.of(exe.toFile())));
        long packed = Files.size(exe);
        run(ExecutableCompressionSupport.upxCommand(BundledTool.UPX.path(), UpxAction.DECOMPRESS, List.of(exe.toFile())));

        assertThat(packed).isLessThan(original);
        assertThat(Files.size(exe)).isGreaterThan(packed);
    }

    @Test
    void pdftkMergesCountsAndExtractsPages() throws Exception {
        LocalFileSystem local = new LocalFileSystem("");
        PdfOperations pdfs = new PdfOperations(registry, new ExternalToolRunner(() -> {}));
        Path first = pdf("first.pdf");
        Path second = pdf("second.pdf");
        Path pages = Files.createDirectory(dir.resolve("pages"));

        pdfs.merge(local, List.of(entry(first), entry(second)), local, dir.toString(), "merged.pdf");
        Path merged = dir.resolve("merged.pdf");
        pdfs.extractPages(local, entry(merged), local, pages.toString(), PdfExtractOptions.extractAll());

        assertThat(pdfs.pageCount(local, entry(merged))).isEqualTo(2);
        try (var files = Files.list(pages)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    void sevenZipOpensAndRepacksAnArchive() throws Exception {
        Path zip = dir.resolve("box.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("a.txt"));
            out.write("before".getBytes(StandardCharsets.UTF_8));
        }
        ArchiveManager archives = new ArchiveManager();

        ArchiveSession session = archives.openArchive(zip.toString());
        Files.writeString(session.getTempFolder().resolve("a.txt"), "after");
        session.setNeedsRepack(true);
        archives.closeArchive(session);
        ArchiveSession reopened = archives.openArchive(zip.toString());

        assertThat(reopened.getTempFolder().resolve("a.txt")).hasContent("after");
        archives.closeArchive(reopened);
    }

    @Test
    void sdeleteWipesAFile() throws Exception {
        Path file = Files.writeString(dir.resolve("secret.txt"), "secret");
        ActionDefinition wipe = registry.findAction("wipeDelete").orElseThrow();

        run(ToolCommandBuilder.buildCommand(wipe.getPath(), wipe.getArgs(), null, Map.of(), List.of(file.toString())));

        assertThat(file).doesNotExist();
    }

    @Test
    void curlSpeaksTheProtocolsOfFtpConnect() throws Exception {
        String protocols = run(List.of(BundledTool.CURL.path().toString(), "--version")).stream()
                .filter(line -> line.startsWith("Protocols:")).findFirst().orElse("");

        assertThat(protocols.split("\\s+")).contains("ftp", "ftps", "sftp");
    }

    /** Output lines of a run that must succeed. */
    private static List<String> run(List<String> command) throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.of(command).mergeStderr().run();
        assertThat(result.exitCode()).as(String.join(" ", command) + "\n" + result.stdoutText()).isZero();
        return result.stdout();
    }

    private Path image(String name, String format) throws IOException {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        image.setRGB(10, 10, 0xFF8800);
        File file = dir.resolve(name).toFile();
        assertThat(ImageIO.write(image, format, file)).isTrue();
        return file.toPath();
    }

    /** A one-page PDF with a correct cross-reference table. */
    private Path pdf(String name) throws IOException {
        List<String> objects = List.of("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format("%010d 00000 n \n", offset)));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return Files.writeString(dir.resolve(name), pdf, StandardCharsets.ISO_8859_1);
    }

    private static ClipboardTransfer.Entry entry(Path file) {
        return new ClipboardTransfer.Entry(file.getFileName().toString(), false, file.toString());
    }
}
