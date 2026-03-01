package org.chaiware.acommander.vfs;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FtpFileSystemTest {

    @Test
    void testUrlGeneration() {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host("51.38.67.129")
                .port(21)
                .username("temp@haifaport.com")
                .password("password")
                .build();
        
        assertEquals("ftp://51.38.67.129:21/path", options.getFullUrl("/path"));
        assertEquals("ftp://51.38.67.129:21/path", options.getFullUrl("path"));
    }

    @Test
    void testSanitizePath() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host("51.38.67.129")
                .port(21)
                .username("user")
                .password("pass")
                .build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        Method sanitizeMethod = FtpFileSystem.class.getDeclaredMethod("sanitizePath", String.class);
        sanitizeMethod.setAccessible(true);
        
        // Basic
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "/path/to/folder"));
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "\\path\\to\\folder"));
        assertEquals("/path/to/folder", sanitizeMethod.invoke(fs, "//path///to/folder"));
        
        // Host IP prefix
        assertEquals("/12345678", sanitizeMethod.invoke(fs, "/51.38.67.129/12345678"));
        assertEquals("/", sanitizeMethod.invoke(fs, "/51.38.67.129"));
        assertEquals("/", sanitizeMethod.invoke(fs, "/51.38.67.129/"));
        
        // Full URL
        assertEquals("/path", sanitizeMethod.invoke(fs, "ftp://51.38.67.129:21/path"));
        assertEquals("/path", sanitizeMethod.invoke(fs, "ftp://51.38.67.129/path"));
        
        // Double slash at start (common when joining URLs)
        assertEquals("/path", sanitizeMethod.invoke(fs, "//path"));
        
        // URL with subfolder that happens to look like the host IP (should not happen, but sanitize should be robust)
        assertEquals("/folder/51.38.67.129/path", sanitizeMethod.invoke(fs, "/folder/51.38.67.129/path"));
    }

    @Test
    void testGetParent() {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host("host").port(21).username("u").password("p").build();
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
                .host("51.38.67.129")
                .port(21)
                .username("user")
                .password("pass")
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
                .host("51.38.67.129")
                .port(21)
                .username("user")
                .password("pass")
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
                .host("51.38.67.129")
                .port(21)
                .username("user")
                .password("pass")
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
                .host("host").port(21).username("u").password("p").build();
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
                .host("host").port(21).username("u").password("p").build();
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
                .host("host").port(21).username("u").password("p").build();
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
    void testGetDisplayName() throws Exception {
        FtpConnectionOptions options = FtpConnectionOptions.builder()
                .host("host").port(21).username("u").password("p").build();
        FtpFileSystem fs = new FtpFileSystem(options);
        
        java.lang.reflect.Field field = FtpFileSystem.class.getDeclaredField("currentInternalPath");
        field.setAccessible(true);
        
        field.set(fs, "/");
        assertEquals("ftp://host/", fs.getDisplayName());
        
        field.set(fs, "/home/user");
        assertEquals("ftp://host/home/user", fs.getDisplayName());
    }
}
