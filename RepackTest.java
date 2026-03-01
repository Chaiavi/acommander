import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RepackTest {
    private static final String SEVEN_Z_PATH = Paths.get(System.getProperty("user.dir"), "apps", "extract_all", "UniExtract", "bin", "x64", "7z.exe").toString();

    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("repack_test_");
        try {
            // 1. Create a zip file
            Path file1 = tempDir.resolve("file1.txt");
            Files.writeString(file1, "content1");
            Path subDir = tempDir.resolve("subdir");
            Files.createDirectory(subDir);
            Path file2 = subDir.resolve("file2.txt");
            Files.writeString(file2, "content2");

            Path archive = tempDir.resolve("test.zip");
            run7z("a", archive.toString(), tempDir.toString() + "\\*");

            System.out.println("Archive created. Files: file1.txt, subdir/file2.txt");

            // 2. Extract to a new temp folder
            Path extractDir = Files.createTempDirectory("extract_test_");
            run7z("x", archive.toString(), "-o" + extractDir.toString());

            // 3. Delete file1.txt in extract folder
            Files.delete(extractDir.resolve("file1.txt"));
            System.out.println("Deleted file1.txt from extract folder.");

            // 4. Repack using the logic from ArchiveManager
            Path parentDir = archive.getParent();
            // Use same extension to avoid 7z format confusion
            String ext = ".zip";
            Path tempArchive = Files.createTempFile(parentDir, "repack_", ext);

            run7z("a", tempArchive.toString(), extractDir.toString() + "\\*");

            Files.move(tempArchive, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Repacked archive.");

            // 5. Verify contents
            List<String> contents = list7z(archive.toString());
            System.out.println("Archive contents after repack:");
            contents.forEach(System.out::println);

            boolean file1Exists = contents.stream().anyMatch(s -> s.contains("file1.txt"));
            boolean file2Exists = contents.stream().anyMatch(s -> s.contains("file2.txt"));

            if (file1Exists) {
                System.err.println("TEST FAILED: file1.txt still exists in archive!");
            } else if (!file2Exists) {
                System.err.println("TEST FAILED: file2.txt is missing from archive!");
            } else {
                System.out.println("TEST PASSED: file1.txt was removed.");
            }

        } finally {
            // Cleanup
            // Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static void run7z(String cmd, String... args) throws Exception {
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add(SEVEN_Z_PATH);
        fullCmd.add(cmd);
        fullCmd.add("-y");
        for (String arg : args) {
            fullCmd.add(arg);
        }
        Process process = new ProcessBuilder(fullCmd).start();
        process.waitFor();
    }

    private static List<String> list7z(String archive) throws Exception {
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add(SEVEN_Z_PATH);
        fullCmd.add("l");
        fullCmd.add(archive);
        Process process = new ProcessBuilder(fullCmd).start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }
        process.waitFor();
        return output;
    }
}
