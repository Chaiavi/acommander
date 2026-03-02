package org.chaiware.acommander.vfs;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FtpFileSystem.
 * <p>
 * SECURITY NOTE: All credentials below are MOCK values for testing only.
 * Never commit real passwords, API keys, or production server addresses.
 */
class FtpFileSystemTest {

    // Mock credentials for testing - never use real credentials in tests
    private static final String MOCK_HOST = "ftp.example.com";
    private static final String MOCK_PASSWORD = "test_password_123";

    @Test
    void testUrlGeneration() {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("testuser@example.com")
                .password(MOCK_PASSWORD)
                .build();

        assertEquals("ftp://ftp.example.com:21/path", options.getFullUrl("/path"));
        assertEquals("ftp://ftp.example.com:21/path", options.getFullUrl("path"));
    }

    @Test
    void testSanitizePath() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("user")
                .password(MOCK_PASSWORD)
                .build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        Method sanitizeMethod = FtpFileSystem.class.getDeclaredMethod("sanitizePath", String.class);
        sanitizeMethod.setAccessible(true);
        
        // Basic
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "/path/to/folder"));
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "\\path\\to\\folder"));
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "//path///to/folder"));
        
        // Host IP prefix (test with mock host)
        assertEquals("/12345678", sanitizeMethod.invoke(fs, "/ftp.example.com/12345678"));
        assertEquals("/", sanitizeMethod.invoke(fs, "/ftp.example.com"));
        assertEquals("/", sanitizeMethod.invoke(fs, "/ftp.example.com/"));

        // Full URL
        assertEquals("/path", sanitizeMethod.invoke(fs, "ftp://ftp.example.com:21/path"));
        assertEquals("/path", sanitizeMethod.invoke(fs, "ftp://ftp.example.com/path"));
        
        // Double slash at start (common when joining URLs)
        assertEquals("/path", sanitizeMethod.invoke(fs, "//path"));
        
        // URL with subfolder that happens to look like the host IP (should not happen, but sanitize should be robust)
        assertEquals("/folder/51.38.67.129/path", sanitizeMethod.invoke(fs, "/folder/51.38.67.129/path"));
    }

    @Test
    void testGetParent() {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        assertEquals("/", fs.getParent("/"));
        assertEquals("/", fs.getParent("/folder"));
        assertEquals("/", fs.getParent("/folder/"));
        assertEquals("/folder", fs.getParent("/folder/subfolder"));
        assertEquals("/folder", fs.getParent("/folder/subfolder/"));
        assertEquals("/", fs.getParent(""));
        assertEquals("/", fs.getParent(null));
    }

    @Test
    void testSanitizePathLocalPath() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("user")
                .password(MOCK_PASSWORD)
                .build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        Method sanitizeMethod = FtpFileSystem.class.getDeclaredMethod("sanitizePath", String.class);
        sanitizeMethod.setAccessible(true);

        // Windows local path - currently it incorrectly adds a leading slash
        // We want to verify this behavior and then fix it if it's applied to local paths in copy/move
        String localPath = "C:\\Users\\temp\\file.txt";
        String sanitized = (String) sanitizeMethod.invoke(fs, localPath);
        assertEquals("/C:/Users/temp/file.txt", sanitized);
    }

    @Test
    void testSanitizePathUnnecessaryForLocalPaths() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("user")
                .password(MOCK_PASSWORD)
                .build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        Method sanitizeMethod = FtpFileSystem.class.getDeclaredMethod("sanitizePath", String.class);
        sanitizeMethod.setAccessible(true);

        // This test documents the behavior of sanitizePath on local paths
        // and why we should avoid calling it on them in copy/move.
        String localPath = "C:\\Users\\temp\\file.txt";
        String sanitized = (String) sanitizeMethod.invoke(fs, localPath);
        
        // It prepends a slash and converts backslashes, which breaks curl on Windows
        // when writing to a local path.
        assertTrue(sanitized.startsWith("/C:"));
    }

    @Test
    void testDeleteRecursiveLogic() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("user")
                .password(MOCK_PASSWORD)
                .build();

        // Subclass to mock/verify behavior
        FtpFileSystem fs = new FtpFileSystem(options) {
            private int curlCount = 0;
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                curlCount++;
                String cmdStr = String.join(" ", command);
                // System.out.println("[DEBUG_LOG] curl " + curlCount + ": " + cmdStr);
                if (cmdStr.contains("DELE /folder/file.txt")) {
                    return List.of(); // success
                }
                if (cmdStr.contains("DELE /folder")) {
                    throw new IOException("DELE failed (is a directory)");
                }
                if (cmdStr.contains("RMD /folder")) {
                    // 1st call: curl 2 (Direct RMD)
                    // 2nd call: curl 5 (Final RMD)
                    if (curlCount <= 2) { 
                         throw new IOException("RMD failed (not empty)");
                    }
                    return List.of();
                }
                if (cmdStr.contains("LIST")) {
                    return List.of("drwxr-xr-x    2 1001     1001         4096 Mar 01 16:39 .",
                                   "drwxr-xr-x    2 1001     1001         4096 Mar 01 16:39 ..",
                                   "-rw-r--r--    1 1001     1001          123 Mar 01 16:39 file.txt");
                }
                return List.of();
            }
        };

        fs.delete("/folder");
    }

    @Test
    void testParseLineUnix() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpFileSystem fs = new FtpFileSystem(options);
        Method parseLine = FtpFileSystem.class.getDeclaredMethod("parseLine", String.class);
        parseLine.setAccessible(true);

        // Unix with HH:mm
        String line1 = "drwxr-xr-x    2 1001     1001         4096 Mar 01 16:39 foldername";
        FileItem item1 = (FileItem) parseLine.invoke(fs, line1);
        assertNotNull(item1);
        assertEquals("foldername", item1.getPresentableFilename());
        assertTrue(item1.isDirectory());
        assertEquals(4096, item1.getSizeInBytes());
        assertTrue(item1.getLastModified() > 0);

        // Unix with yyyy
        String line2 = "-rw-r--r--    1 1001     1001          123 Mar 01  2024 filename.txt";
        FileItem item2 = (FileItem) parseLine.invoke(fs, line2);
        assertNotNull(item2);
        assertEquals("filename.txt", item2.getPresentableFilename());
        assertFalse(item2.isDirectory());
        assertEquals(123, item2.getSizeInBytes());
        assertTrue(item2.getLastModified() > 0);

        // Unix with Case Sensitivity (Lowercase month)
        String line3 = "drwxr-xr-x    2 1001     1001         4096 mar 01 16:39 foldername";
        FileItem item3 = (FileItem) parseLine.invoke(fs, line3);
        assertNotNull(item3);
        assertEquals("foldername", item3.getPresentableFilename());
        assertTrue(item3.getLastModified() > 0);
    }

    @Test
    void testParseLineDos() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpFileSystem fs = new FtpFileSystem(options);
        Method parseLine = FtpFileSystem.class.getDeclaredMethod("parseLine", String.class);
        parseLine.setAccessible(true);

        // DOS Directory
        String line1 = "03-01-26  05:01PM       <DIR>          foldername";
        FileItem item1 = (FileItem) parseLine.invoke(fs, line1);
        assertNotNull(item1);
        assertEquals("foldername", item1.getPresentableFilename());
        assertTrue(item1.isDirectory());
        assertTrue(item1.getLastModified() > 0);

        // DOS File
        String line2 = "03-01-26  05:01PM                12345 filename.ext";
        FileItem item2 = (FileItem) parseLine.invoke(fs, line2);
        assertNotNull(item2);
        assertEquals("filename.ext", item2.getPresentableFilename());
        assertFalse(item2.isDirectory());
        assertEquals(12345, item2.getSizeInBytes());
        assertTrue(item2.getLastModified() > 0);

        // DOS File with lowercase pm
        String line3 = "03-01-26  05:01pm                12345 filename.ext";
        FileItem item3 = (FileItem) parseLine.invoke(fs, line3);
        assertNotNull(item3);
        assertEquals("filename.ext", item3.getPresentableFilename());
        assertTrue(item3.getLastModified() > 0);
    }

    @Test
    void testGetInternalPath() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        // Mock currentInternalPath by listing (which sets it)
        // Or we can just use reflection to set it, but listContents sets it.
        // Let's use reflection to be sure.
        java.lang.reflect.Field field = FtpFileSystem.class.getDeclaredField("currentInternalPath");
        field.setAccessible(true);
        field.set(fs, "/home/user");
        
        FileItem item = new FileItem(null, "subfolder", 0, 0, true);
        String path = fs.getInternalPath(item);
        assertEquals("/home/user/subfolder", path);
        
        field.set(fs, "/");
        path = fs.getInternalPath(item);
        assertEquals("/subfolder", path);
        
        FileItem parentItem = new FileItem(null, "..", 0, 0, true);
        field.set(fs, "/home/user");
        path = fs.getInternalPath(parentItem);
        assertEquals("/home", path);
    }

    @Test
    void testCopyRecursiveLogic() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();

        final List<String> commands = new ArrayList<>();
        FtpFileSystem fs = new FtpFileSystem(options) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                String cmdStr = String.join(" ", command);
                commands.add(cmdStr);
                if (cmdStr.contains("/source/sub")) {
                    return List.of("-rw-r--r--    1 u u  456 Mar 01 16:39 subfile.txt");
                }
                if (cmdStr.contains("/source")) {
                    return List.of("drwxr-xr-x    2 u u 4096 Mar 01 16:39 .",
                                   "drwxr-xr-x    2 u u 4096 Mar 01 16:39 ..",
                                   "-rw-r--r--    1 u u  123 Mar 01 16:39 file.txt",
                                   "drwxr-xr-x    2 u u 4096 Mar 01 16:39 sub");
                }
                if (cmdStr.contains("ftp://" + MOCK_HOST + ":21/")) {
                    return List.of("drwxr-xr-x    2 u u 4096 Mar 01 16:39 source");
                }
                return List.of();
            }
        };

        FtpFileSystem targetFs = new FtpFileSystem(options) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                commands.add("TARGET: " + String.join(" ", command));
                return List.of();
            }
        };

        fs.copy("/source", targetFs, "/target");

        // Verify MKD (makeDirectory) calls
        assertTrue(commands.stream().anyMatch(c -> c.contains("MKD /target")), "Should create target dir");
        assertTrue(commands.stream().anyMatch(c -> c.contains("MKD /target/sub")), "Should create target subdir");
        
        // Verify file copies (will involve temp files, but we check curl -T)
        assertTrue(commands.stream().anyMatch(c -> c.contains("-T") && c.contains("/target/file.txt")), "Should upload file.txt");
        assertTrue(commands.stream().anyMatch(c -> c.contains("-T") && c.contains("/target/sub/subfile.txt")), "Should upload subfile.txt");
    }

    @Test
    void testMoveSameServerOptimized() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();

        final List<String> commands = new ArrayList<>();
        FtpFileSystem fs = new FtpFileSystem(options) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                commands.add(String.join(" ", command));
                return List.of();
            }
        };

        fs.move("/source.txt", fs, "/dest.txt");

        // Should use rename (RNFR/RNTO)
        assertTrue(commands.stream().anyMatch(c -> c.contains("RNFR /source.txt")), "Should use RNFR");
        assertTrue(commands.stream().anyMatch(c -> c.contains("RNTO /dest.txt")), "Should use RNTO");
        // Should NOT use DELE or -T
        assertFalse(commands.stream().anyMatch(c -> c.contains("DELE")), "Should not delete");
        assertFalse(commands.stream().anyMatch(c -> c.contains("-T")), "Should not upload");
    }
    @Test
    void testMoveDifferentServers() throws Exception {
        FtpConnectionOptions options1 = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpConnectionOptions options2 = FtpConnectionOptions.builder()
                .host("ftp2.example.com").port(21).username("u").password(MOCK_PASSWORD).build();

        final List<String> commands = new ArrayList<>();
        FtpFileSystem fs1 = new FtpFileSystem(options1) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                String cmdStr = String.join(" ", command);
                commands.add("FS1: " + cmdStr);
                if (cmdStr.contains("LIST") || (cmdStr.contains("ftp://") && cmdStr.contains("ftp.example.com") && !cmdStr.contains("-Q") && !cmdStr.contains("-T") && !cmdStr.contains("-X") && !cmdStr.contains("-o"))) {
                    return List.of("-rw-r--r--    1 u u  123 Mar 01 16:39 source.txt");
                }
                return List.of();
            }
        };

        FtpFileSystem fs2 = new FtpFileSystem(options2) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                commands.add("FS2: " + String.join(" ", command));
                return List.of();
            }
        };

        fs1.move("/source.txt", fs2, "/dest.txt");

        // System.out.println("[DEBUG_LOG] Commands: " + String.join("\n", commands));

        // Should NOT use rename
        assertFalse(commands.stream().anyMatch(c -> c.contains("RNFR")), "Should not use RNFR for different servers");

        // Should use:
        // 1. Download from FS1 to temp
        // 2. Upload from temp to FS2
        // 3. Delete from FS1

        assertTrue(commands.stream().anyMatch(c -> c.contains("FS1:") && c.contains("-o") && c.contains("source.txt")), "Should download from FS1");
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS2:") && c.contains("-T") && c.contains("dest.txt")), "Should upload to FS2");
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS1:") && c.contains("DELE /source.txt")), "Should delete from FS1");
    }

    @Test
    void testMoveDifferentServersWithUnsanitizedTarget() throws Exception {
        FtpConnectionOptions options1 = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("u").password(MOCK_PASSWORD).build();
        FtpConnectionOptions options2 = FtpConnectionOptions.builder()
                .host("ftp2.example.com").port(21).username("u").password(MOCK_PASSWORD).build();

        final List<String> commands = new ArrayList<>();
        FtpFileSystem fs1 = new FtpFileSystem(options1) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                String cmdStr = String.join(" ", command);
                commands.add("FS1: " + cmdStr);
                if (cmdStr.contains("LIST") || (cmdStr.contains("ftp://") && cmdStr.contains("ftp.example.com") && !cmdStr.contains("-Q") && !cmdStr.contains("-T") && !cmdStr.contains("-X") && !cmdStr.contains("-o"))) {
                    return List.of("-rw-r--r--    1 u u  123 Mar 01 16:39 source.txt");
                }
                return List.of();
            }
        };

        FtpFileSystem fs2 = new FtpFileSystem(options2) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                commands.add("FS2: " + String.join(" ", command));
                return List.of();
            }
        };

        // UI might pass a full URL as target path when dragging between panes
        String unsanitizedTarget = "ftp://ftp2.example.com:21/dest.txt";
        fs1.move("/source.txt", fs2, unsanitizedTarget);

        // Verify that target path was sanitized before being used in FS2
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS2:") && c.contains("-T") && c.contains("ftp2.example.com")), "Should upload to sanitized FS2 URL");
        // Verify we didn't end up with double URL in target
        assertFalse(commands.stream().anyMatch(c -> c.contains("FS2:") && c.contains("ftp2.example.com//ftp:")), "Should not have double URL in target");
    }

    @Test
    void testMoveSameHostDifferentUser() throws Exception {
        FtpConnectionOptions options1 = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("user1").password(MOCK_PASSWORD).build();
        FtpConnectionOptions options2 = FtpConnectionOptions.builder()
                .host(MOCK_HOST).port(21).username("user2").password(MOCK_PASSWORD).build();

        final List<String> commands = new ArrayList<>();
        FtpFileSystem fs1 = new FtpFileSystem(options1) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                String cmdStr = String.join(" ", command);
                commands.add("FS1: " + cmdStr);
                if (cmdStr.contains("LIST") || (cmdStr.contains("ftp://") && cmdStr.contains(MOCK_HOST) && !cmdStr.contains("-Q") && !cmdStr.contains("-T") && !cmdStr.contains("-X") && !cmdStr.contains("-o"))) {
                    return List.of("-rw-r--r--    1 user1 user1  123 Mar 01 16:39 file.txt");
                }
                return List.of();
            }
        };

        FtpFileSystem fs2 = new FtpFileSystem(options2) {
            @Override
            public List<String> runCurl(List<String> command) throws IOException {
                commands.add("FS2: " + String.join(" ", command));
                return List.of();
            }
        };

        fs1.move("/file.txt", fs2, "/file.txt");

        // Should NOT use rename (RNFR/RNTO) because credentials differ
        assertFalse(commands.stream().anyMatch(c -> c.contains("RNFR")), "Should not use RNFR for different users on same host");

        // Should use copy + delete
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS1:") && c.contains("-o")), "Should download from user1");
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS2:") && c.contains("-T")), "Should upload to user2");
        assertTrue(commands.stream().anyMatch(c -> c.contains("FS1:") && c.contains("DELE /file.txt")), "Should delete from user1");
    }

    @Test
    void testProtocolEnum() {
        // Test FTP protocol
        assertEquals("ftp", FtpConnectionOptions.Protocol.FTP.getScheme());
        assertEquals(21, FtpConnectionOptions.Protocol.FTP.getDefaultPort());

        // Test SFTP protocol
        assertEquals("sftp", FtpConnectionOptions.Protocol.SFTP.getScheme());
        assertEquals(22, FtpConnectionOptions.Protocol.SFTP.getDefaultPort());

        // Test FTPS protocol (explicit FTPS uses ftp:// scheme with --ssl --ssl-reqd)
        assertEquals("ftp", FtpConnectionOptions.Protocol.FTPS.getScheme());
        assertEquals(21, FtpConnectionOptions.Protocol.FTPS.getDefaultPort());
    }

    @Test
    void testUrlGenerationWithProtocol() {
        // Test FTP URL
        FtpConnectionOptions ftpOptions = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("testuser")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.FTP)
                .build();
        assertEquals("ftp://ftp.example.com:21/path", ftpOptions.getFullUrl("/path"));

        // Test SFTP URL
        FtpConnectionOptions sftpOptions = FtpConnectionOptions.builder()
                .host("sftp.example.com")
                .port(22)
                .username("testuser")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.SFTP)
                .build();
        assertEquals("sftp://sftp.example.com:22/path", sftpOptions.getFullUrl("/path"));

        // Test FTPS URL (explicit FTPS uses ftp:// with --ssl --ssl-reqd)
        FtpConnectionOptions ftpsOptions = FtpConnectionOptions.builder()
                .host("ftps.example.com")
                .port(21)
                .username("testuser")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.FTPS)
                .build();
        assertEquals("ftp://ftps.example.com:21/path", ftpsOptions.getFullUrl("/path"));
    }

    @Test
    void testDefaultProtocolIsFtp() {
        // When protocol is not specified, default should be FTP
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("testuser")
                .password(MOCK_PASSWORD)
                .build();
        assertEquals(FtpConnectionOptions.Protocol.FTP, options.getProtocol());
    }

    @Test
    void testSanitizePathWithDifferentProtocols() throws Exception {
        // Test SFTP path sanitization
        FtpConnectionOptions sftpOptions = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(22)
                .username("user")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.SFTP)
                .build();
        FtpFileSystem sftpFs = new FtpFileSystem(sftpOptions);
        Method sanitizeMethod = FtpFileSystem.class.getDeclaredMethod("sanitizePath", String.class);
        sanitizeMethod.setAccessible(true);
        assertEquals("/path", sanitizeMethod.invoke(sftpFs, "sftp://ftp.example.com:22/path"));

        // Test FTPS path sanitization (explicit FTPS uses ftp:// scheme)
        FtpConnectionOptions ftpsOptions = FtpConnectionOptions.builder()
                .host(MOCK_HOST)
                .port(21)
                .username("user")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.FTPS)
                .build();
        FtpFileSystem ftpsFs = new FtpFileSystem(ftpsOptions);
        assertEquals("/path", sanitizeMethod.invoke(ftpsFs, "ftp://ftp.example.com:21/path"));
    }

    @Test
    void testGetIdentifierAndDisplayNameWithProtocol() {
        FtpConnectionOptions sftpOptions = FtpConnectionOptions.builder()
                .host("sftp.example.com")
                .port(22)
                .username("testuser")
                .password(MOCK_PASSWORD)
                .protocol(FtpConnectionOptions.Protocol.SFTP)
                .build();
        FtpFileSystem fs = new FtpFileSystem(sftpOptions);

        assertTrue(fs.getIdentifier().startsWith("sftp:"));
        assertTrue(fs.getDisplayName().startsWith("sftp://"));
    }
}
