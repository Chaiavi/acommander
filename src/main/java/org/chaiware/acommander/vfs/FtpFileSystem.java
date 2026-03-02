package org.chaiware.acommander.vfs;

import org.chaiware.acommander.commands.ExternalCommandListener;
import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpFileSystem implements VFileSystem {
    private static final Logger logger = LoggerFactory.getLogger(FtpFileSystem.class);
    private final FtpConnectionOptions options;
    private final String curlPath;
    private String currentInternalPath = "/";
    private ExternalCommandListener externalCommandListener;

    // Regex for standard Unix-style FTP list (LS -l)
    // Example: drwxr-xr-x    2 1001     1001         4096 Mar 01 16:39 foldername
    private static final Pattern UNIX_LIST_PATTERN = Pattern.compile(
            "^([bcdlpsD-])" +                         // 1: type
            "([rwx-]{3}){3}\\s+" +                    // 2: permissions (simplified)
            "\\d+\\s+" +                              // links
            "[^\\s]+\\s+" +                           // owner
            "[^\\s]+\\s+" +                           // group
            "(\\d+)\\s+" +                            // 3: size
            "(\\w{3}\\s+\\d+\\s+[:\\d]+|\\w{3}\\s+\\d+\\s+\\d{4})\\s+" + // 4: date/time
            "(.+)$"                                   // 5: name
    );

    // Regex for Windows/MS-DOS style FTP list
    // Example: 03-01-26  05:01PM       <DIR>          foldername
    // Example: 03-01-26  05:01PM                12345 filename.ext
    private static final Pattern MS_DOS_LIST_PATTERN = Pattern.compile(
            "^(\\d{2}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}[APap][Mm])\\s+" + // 1: date/time (case-insensitive AM/PM)
            "(<DIR>|\\d+)\\s+" +                                // 2: dir indicator or size
            "(.+)$"                                             // 3: name
    );

    private static final DateTimeFormatter UNIX_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d ")
            .optionalStart()
            .appendPattern("HH:mm")
            .optionalEnd()
            .optionalStart()
            .appendPattern("yyyy")
            .optionalEnd()
            .parseDefaulting(ChronoField.YEAR, LocalDateTime.now().getYear())
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter UNIX_DATE_YEAR_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d yyyy")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter DOS_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MM-dd-yy  hh:mma")
            .toFormatter(Locale.ENGLISH);

    public FtpFileSystem(FtpConnectionOptions options) {
        this.options = options;
        this.curlPath = Paths.get(System.getProperty("user.dir"), "apps", "remote_connectivity", "curl.exe").toString();
    }

    @Override
    public void setExternalCommandListener(ExternalCommandListener listener) {
        this.externalCommandListener = listener;
    }

    @Override
    public String getIdentifier() {
        return "ftp:" + options.getHost() + ":" + options.getPort() + currentInternalPath;
    }

    @Override
    public String getDisplayName() {
        String path = currentInternalPath;
        if (path == null) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        return "ftp://" + options.getHost() + path;
    }

    @Override
    public List<FileItem> listContents(String internalPath) throws IOException {
        String cleanPath = sanitizePath(internalPath);
        logger.info("Listing contents of FTP path: {} (original: {})", cleanPath, internalPath);
        
        this.currentInternalPath = cleanPath;
        if (!currentInternalPath.endsWith("/")) {
            currentInternalPath += "/";
        }
        
        List<String> command = new ArrayList<>();
        command.add(curlPath);
        command.add("--silent");
        command.add("-u");
        command.add(options.getUsername() + ":" + options.getPassword());
        command.add(options.getFullUrl(currentInternalPath));
        command.add("--ftp-pasv");
        
        List<String> output = runCurl(command);
        List<FileItem> items = new ArrayList<>();
        
        // Add ".."
        if (!"/".equals(currentInternalPath)) {
            items.add(new FileItem(null, "..", 0, 0, true));
        }

        for (String line : output) {
            FileItem item = parseLine(line);
            if (item != null) {
                items.add(item);
            }
        }
        
        logger.info("Found {} items in {}", items.size(), internalPath);
        return items;
    }

    private FileItem parseLine(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("% Total") || line.contains("Dload  Upload")) {
            return null;
        }

        Matcher unixMatcher = UNIX_LIST_PATTERN.matcher(line);
        if (unixMatcher.find()) {
            String type = unixMatcher.group(1);
            boolean isDir = "d".equals(type);
            long size = Long.parseLong(unixMatcher.group(3));
            String dateStr = unixMatcher.group(4).replaceAll("\\s+", " ");
            String name = unixMatcher.group(5).trim();
            if (".".equals(name) || "..".equals(name)) return null;

            long lastModified = 0;
            try {
                if (dateStr.contains(":")) {
                    lastModified = LocalDateTime.parse(dateStr, UNIX_DATE_TIME_FORMATTER)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                } else {
                    lastModified = LocalDateTime.parse(dateStr, UNIX_DATE_YEAR_FORMATTER)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
            } catch (Exception e) {
                logger.warn("Failed to parse Unix FTP date: {}", dateStr);
            }

            return new FileItem(null, name, size, lastModified, isDir);
        }

        Matcher dosMatcher = MS_DOS_LIST_PATTERN.matcher(line);
        if (dosMatcher.find()) {
            String dateStr = dosMatcher.group(1);
            String dirOrSize = dosMatcher.group(2);
            boolean isDir = "<DIR>".equalsIgnoreCase(dirOrSize);
            long size = isDir ? 0 : Long.parseLong(dirOrSize);
            String name = dosMatcher.group(3).trim();
            if (".".equals(name) || "..".equals(name)) return null;

            long lastModified = 0;
            try {
                lastModified = LocalDateTime.parse(dateStr, DOS_DATE_TIME_FORMATTER)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                logger.warn("Failed to parse DOS FTP date: {}", dateStr);
            }

            return new FileItem(null, name, size, lastModified, isDir);
        }

        return null;
    }

    private static final Map<Integer, String> CURL_ERROR_CODES = Map.ofEntries(
        Map.entry(0, "Success (CURLE_OK)"),
        Map.entry(1, "Unsupported protocol"),
        Map.entry(2, "Failed to initialize"),
        Map.entry(3, "URL malformed"),
        Map.entry(4, "Requested feature not built in"),
        Map.entry(5, "Couldn’t resolve proxy"),
        Map.entry(6, "Couldn’t resolve host"),
        Map.entry(7, "Failed to connect"),
        Map.entry(8, "Weird server reply"),
        Map.entry(9, "Remote access denied"),
        Map.entry(10, "FTP accept failed"),
        Map.entry(11, "FTP weird PASS reply"),
        Map.entry(12, "FTP accept timeout"),
        Map.entry(13, "FTP weird PASV reply"),
        Map.entry(14, "FTP weird 227 format"),
        Map.entry(15, "FTP can’t get host"),
        Map.entry(16, "HTTP/2 framing error"),
        Map.entry(17, "FTP couldn’t set type"),
        Map.entry(18, "Partial file transferred"),
        Map.entry(19, "FTP couldn’t RETR file"),
        Map.entry(21, "QUOTE command error"),
        Map.entry(22, "HTTP returned error (with --fail)"),
        Map.entry(23, "Write error"),
        Map.entry(25, "FTP upload failed"),
        Map.entry(26, "Read error"),
        Map.entry(27, "Out of memory"),
        Map.entry(28, "Operation timed out"),
        Map.entry(30, "FTP PORT failed"),
        Map.entry(31, "FTP couldn’t use REST"),
        Map.entry(33, "Range error"),
        Map.entry(35, "SSL connect error"),
        Map.entry(36, "Bad download resume"),
        Map.entry(37, "File couldn’t be read"),
        Map.entry(38, "LDAP cannot bind"),
        Map.entry(39, "LDAP search failed"),
        Map.entry(42, "Aborted by callback"),
        Map.entry(43, "Bad function argument"),
        Map.entry(45, "Interface error"),
        Map.entry(47, "Too many redirects"),
        Map.entry(48, "Unknown option passed"),
        Map.entry(49, "Malformed option syntax"),
        Map.entry(52, "Server returned nothing"),
        Map.entry(53, "SSL engine not found"),
        Map.entry(54, "Cannot set SSL engine default"),
        Map.entry(55, "Send error"),
        Map.entry(56, "Receive error"),
        Map.entry(58, "SSL certificate problem"),
        Map.entry(59, "SSL cipher problem"),
        Map.entry(60, "Peer certificate failed verification"),
        Map.entry(61, "Bad content encoding"),
        Map.entry(63, "File size exceeded"),
        Map.entry(64, "Use SSL failed"),
        Map.entry(65, "Send fail rewind"),
        Map.entry(66, "SSL engine init failed"),
        Map.entry(67, "Login denied"),
        Map.entry(68, "TFTP not found"),
        Map.entry(69, "TFTP permission problem"),
        Map.entry(70, "Remote disk full"),
        Map.entry(71, "TFTP illegal operation"),
        Map.entry(72, "TFTP unknown transfer ID"),
        Map.entry(73, "Remote file exists"),
        Map.entry(87, "FTP bad file list"),
        Map.entry(88, "Chunk callback failed"),
        Map.entry(89, "No connection available"),
        Map.entry(90, "SSL pinned public key not match"),
        Map.entry(91, "SSL invalid certificate status"),
        Map.entry(92, "HTTP/2 stream error"),
        Map.entry(93, "Recursive API call"),
        Map.entry(94, "Authentication error"),
        Map.entry(95, "HTTP/3 error"),
        Map.entry(96, "QUIC connect error"),
        Map.entry(97, "Proxy handshake error"),
        Map.entry(98, "SSL client certificate required"),
        Map.entry(99, "Unrecoverable poll error"),
        Map.entry(100, "Value too large"),
        Map.entry(101, "ECH required")
    );

    private String getCurlErrorMessage(int exitCode, List<String> output) {
        String descriptiveError = CURL_ERROR_CODES.getOrDefault(exitCode, "Unknown error (" + exitCode + ")");
        String curlOutput = String.join(" ", output).trim();
        if (curlOutput.isEmpty()) {
            return descriptiveError;
        }
        return descriptiveError + ": " + curlOutput;
    }

    public List<String> runCurl(List<String> command) throws IOException {
        logger.debug("Executing FTP command: {}", obfuscateCommand(command));
        if (externalCommandListener != null) {
            externalCommandListener.onCommandStarted(command);
        }
        
        int exitCode = -1;
        List<String> output = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            try {
                exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.error("curl failed with exit code {}. Output: {}", exitCode, String.join("\n", output));
                    throw new IOException("FTP operation failed: " + getCurlErrorMessage(exitCode, output));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("FTP operation interrupted", e);
            }
            
            if (externalCommandListener != null) {
                externalCommandListener.onCommandFinished(command, exitCode, null);
            }
            return output;
        } catch (IOException e) {
            if (externalCommandListener != null) {
                externalCommandListener.onCommandFinished(command, exitCode, e);
            }
            throw e;
        }
    }

    private String obfuscateCommand(List<String> command) {
        List<String> obfuscated = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if (i > 0 && "-u".equals(command.get(i - 1))) {
                int colon = arg.indexOf(':');
                if (colon >= 0) {
                    obfuscated.add(arg.substring(0, colon + 1) + "********");
                } else {
                    obfuscated.add("********");
                }
            } else {
                obfuscated.add(arg);
            }
        }
        return String.join(" ", obfuscated);
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void delete(String internalPath) throws IOException {
        String ftpPath = sanitizePath(internalPath);
        logger.info("Deleting FTP item: {}", ftpPath);

        // Try DELE first (for files)
        List<String> command = createBaseCurlCommand();
        command.add(options.getUrl() + "/");
        command.add("-Q");
        command.add("DELE " + ftpPath);

        try {
            runCurl(command);
            logger.debug("Successfully deleted (DELE) {}", ftpPath);
        } catch (IOException e) {
            // If DELE fails, it might be a directory or a non-existent file
            logger.debug("DELE failed for {}, trying recursive RMD", ftpPath);
            deleteRecursive(ftpPath);
        }
    }

    private void deleteRecursive(String ftpPath) throws IOException {
        // 1. Try RMD directly first (in case it's empty)
        try {
            List<String> command = createBaseCurlCommand();
            command.add(options.getUrl() + "/");
            command.add("-Q");
            command.add("RMD " + ftpPath);
            runCurl(command);
            logger.debug("Successfully deleted empty directory (RMD) {}", ftpPath);
            return;
        } catch (IOException e) {
            // Not empty or not a directory
            logger.debug("Direct RMD failed for {}, attempting recursive delete", ftpPath);
        }

        // 2. List contents
        List<FileItem> contents;
        try {
            contents = listContents(ftpPath);
        } catch (IOException e) {
            // If we can't list it, maybe it's not a directory or doesn't exist.
            // But we already tried DELE and RMD. Throw the original error or this one.
            throw new IOException("Failed to delete " + ftpPath + ": " + e.getMessage(), e);
        }

        // 3. Delete children
        for (FileItem item : contents) {
            if ("..".equals(item.getPresentableFilename())) continue;
            String childPath = ftpPath;
            if (!childPath.endsWith("/")) childPath += "/";
            childPath += item.getName();
            
            if (item.isDirectory()) {
                deleteRecursive(childPath);
            } else {
                List<String> delCmd = createBaseCurlCommand();
                delCmd.add(options.getUrl() + "/");
                delCmd.add("-Q");
                delCmd.add("DELE " + sanitizePath(childPath));
                runCurl(delCmd);
            }
        }

        // 4. Finally delete the directory itself
        List<String> finalRmdCmd = createBaseCurlCommand();
        finalRmdCmd.add(options.getUrl() + "/");
        finalRmdCmd.add("-Q");
        finalRmdCmd.add("RMD " + ftpPath);
        runCurl(finalRmdCmd);
        logger.debug("Successfully deleted directory recursively (RMD) {}", ftpPath);
    }

    @Override
    public void move(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        sourceInternalPath = sanitizePath(sourceInternalPath);
        if (targetFs instanceof FtpFileSystem targetFtpFs && 
            targetFtpFs.getOptions().equals(this.getOptions())) {
            // Same server AND same user - use RENAME (RNFR/RNTO)
            targetInternalPath = sanitizePath(targetInternalPath);
            rename(sourceInternalPath, targetInternalPath);
        } else {
            // For different servers or different users, copy will handle sanitization of targetInternalPath
            copy(sourceInternalPath, targetFs, targetInternalPath);
            delete(sourceInternalPath);
        }
    }

    @Override
    public void copy(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        sourceInternalPath = sanitizePath(sourceInternalPath);
        boolean isDir = isDirectory(sourceInternalPath);
        
        if (targetFs instanceof FtpFileSystem targetFtpFs) {
            targetInternalPath = targetFtpFs.sanitizePath(targetInternalPath);
        }
        
        logger.info("Copying {} item from {}:{} to {}:{}", 
                isDir ? "directory" : "file",
                this.getDisplayName(), sourceInternalPath, targetFs.getDisplayName(), targetInternalPath);
        
        if (isDir) {
            copyDirectoryRecursive(sourceInternalPath, targetFs, targetInternalPath);
            return;
        }

        // Single file copy
        copyFile(sourceInternalPath, targetFs, targetInternalPath);
    }

    private boolean isDirectory(String internalPath) throws IOException {
        if ("/".equals(internalPath) || internalPath.isEmpty()) return true;
        
        String parent = getParent(internalPath);
        String name = getName(internalPath);

        List<FileItem> items = listContents(parent);
        for (FileItem item : items) {
            if (name.equals(item.getPresentableFilename())) {
                return item.isDirectory();
            }
        }
        // Fallback: try to list it as a directory. If it succeeds, it's a directory.
        try {
            listContents(internalPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void copyFile(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        if (targetFs instanceof LocalFileSystem) {
            // Download from FTP to local
            logger.debug("Downloading {} to {}", sourceInternalPath, targetInternalPath);
            List<String> command = createBaseCurlCommand();
            command.add(options.getFullUrl(sourceInternalPath));
            command.add("-o");
            command.add(targetInternalPath);
            runCurl(command);
        } else if (targetFs instanceof FtpFileSystem targetFtpFs) {
            // Copy between FTP servers (or same server)
            File tempFile = File.createTempFile("acommander_ftp_transfer", ".tmp");
            try {
                // 1. Download to local temp
                this.copyFile(sourceInternalPath, new LocalFileSystem(""), tempFile.getAbsolutePath());
                // 2. Upload from local temp to target FTP
                List<String> uploadCmd = targetFtpFs.createBaseCurlCommand();
                uploadCmd.add("-T");
                uploadCmd.add(tempFile.getAbsolutePath());
                uploadCmd.add(targetFtpFs.options.getFullUrl(targetInternalPath));
                targetFtpFs.runCurl(uploadCmd);
            } finally {
                tempFile.delete();
            }
        } else if (targetFs instanceof ArchiveFileSystem archiveFs) {
            // Download from FTP to archive temp folder
            Path targetPathInTemp = archiveFs.getSession().getTempFolder().resolve(targetInternalPath);
            Files.createDirectories(targetPathInTemp.getParent());
            this.copyFile(sourceInternalPath, new LocalFileSystem(""), targetPathInTemp.toString());
            archiveFs.markModified();
        } else {
            // Generic target FS: download to temp and let targetFs handle it if it can
            File tempFile = File.createTempFile("acommander_generic_transfer", ".tmp");
            try {
                this.copyFile(sourceInternalPath, new LocalFileSystem(""), tempFile.getAbsolutePath());
                targetFs.copy(tempFile.getAbsolutePath(), targetFs, targetInternalPath);
            } finally {
                tempFile.delete();
            }
        }
    }

    private void copyDirectoryRecursive(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        // Create target directory
        targetFs.makeDirectory(targetInternalPath);

        // List contents of source directory
        List<FileItem> items = listContents(sourceInternalPath);
        for (FileItem item : items) {
            if ("..".equals(item.getPresentableFilename())) continue;

            String itemName = item.getPresentableFilename();
            String subSource = sourceInternalPath + (sourceInternalPath.endsWith("/") ? "" : "/") + itemName;
            String subTarget = targetInternalPath + (targetFs.getSeparator().equals("/") ? "/" : targetFs.getSeparator()) + itemName;

            if (item.isDirectory()) {
                copyDirectoryRecursive(subSource, targetFs, subTarget);
            } else {
                copyFile(subSource, targetFs, subTarget);
            }
        }
    }

    @Override
    public String getInternalPath(FileItem item) {
        if (item == null) return "/";
        String name = item.getName();
        if (name == null) return "/";
        
        if ("..".equals(item.getPresentableFilename())) {
            return getParent(currentInternalPath);
        }
        
        // If it's a full URL, extract the internal path
        String urlPrefix = options.getUrl();
        if (name.contains(urlPrefix)) {
            int index = name.indexOf(urlPrefix) + urlPrefix.length();
            return sanitizePath(name.substring(index));
        }

        // Check if it's already an absolute path within FTP (starts with /)
        if (name.startsWith("/")) {
            return sanitizePath(name);
        }

        // For local files that might have been created as "pseudo-files"
        if (item.getFile() != null) {
            String absPath = item.getFile().getAbsolutePath();
            if (absPath.contains(urlPrefix)) {
                int index = absPath.indexOf(urlPrefix) + urlPrefix.length();
                return sanitizePath(absPath.substring(index));
            }
        }

        // Otherwise, append to current path
        String path = currentInternalPath;
        if (!path.endsWith("/")) {
            path += "/";
        }
        
        return sanitizePath(path + name);
    }

    @Override
    public void rename(String oldInternalPath, String newInternalPath) throws IOException {
        String oldFtpPath = sanitizePath(oldInternalPath);
        String newFtpPath = sanitizePath(newInternalPath);
        logger.info("Renaming FTP item from {} to {}", oldFtpPath, newFtpPath);
        
        List<String> command = createBaseCurlCommand();
        command.add(options.getUrl() + "/");
        command.add("-Q");
        command.add("RNFR " + oldFtpPath);
        command.add("-Q");
        command.add("RNTO " + newFtpPath);
        runCurl(command);
    }

    @Override
    public void makeDirectory(String internalPath) throws IOException {
        String ftpPath = sanitizePath(internalPath);
        logger.info("Creating FTP directory: {}", ftpPath);
        List<String> command = createBaseCurlCommand();
        // Use the root URL to ensure curl doesn't fail if the parent path is not a file
        command.add(options.getUrl() + "/");
        command.add("-Q");
        command.add("MKD " + ftpPath);
        runCurl(command);
    }

    @Override
    public void makeFile(String internalPath) throws IOException {
        String ftpPath = sanitizePath(internalPath);
        logger.info("Creating empty FTP file: {}", ftpPath);
        // Create empty file locally and upload
        File tempFile = File.createTempFile("acommander_empty", ".tmp");
        try {
            List<String> command = createBaseCurlCommand();
            command.add("-T");
            command.add(tempFile.getAbsolutePath());
            command.add(options.getFullUrl(ftpPath));
            runCurl(command);
        } finally {
            tempFile.delete();
        }
    }

    public String sanitizePath(String path) {
        if (path == null) return "";
        String sanitized = path.replace('\\', '/');
        
        // Strip ftp://host:port prefix if present
        String urlPrefix = options.getUrl();
        if (sanitized.startsWith(urlPrefix)) {
            sanitized = sanitized.substring(urlPrefix.length());
        }
        
        // Strip ftp://host prefix (without port) if present
        String hostPrefix = "ftp://" + options.getHost();
        if (sanitized.startsWith(hostPrefix)) {
            sanitized = sanitized.substring(hostPrefix.length());
        }

        // Handle cases where the path might start with the host name/IP directly (e.g. /51.38.67.129/path)
        String host = options.getHost();
        if (sanitized.startsWith("/" + host + "/")) {
            sanitized = sanitized.substring(host.length() + 1);
        } else if (sanitized.equals("/" + host)) {
            sanitized = "/";
        }

        // Remove redundant slashes and ensure it starts with /
        sanitized = sanitized.replaceAll("/+", "/");
        if (!sanitized.startsWith("/")) {
            sanitized = "/" + sanitized;
        }

        return sanitized;
    }

    public FtpConnectionOptions getOptions() {
        return options;
    }

    @Override
    public boolean isVirtualFolder(FileItem item) {
        String name = item.getName().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".tar") || name.endsWith(".gz");
    }

    @Override
    public VFileSystem enterVirtualFolder(FileItem item) throws IOException {
        // To enter an archive on FTP, we first download it to a temp file
        File tempFile = File.createTempFile("acommander_ftp_vfs_", "_" + item.getName());
        tempFile.deleteOnExit();
        
        this.copy(getInternalPath(item), new LocalFileSystem(""), tempFile.getAbsolutePath());
        
        ArchiveManager archiveManager = new ArchiveManager();
        ArchiveSession session = archiveManager.openArchive(tempFile.getAbsolutePath());
        return new ArchiveFileSystem(session, archiveManager);
    }

    @Override
    public void repack() throws IOException {}

    @Override
    public void close() throws IOException {}

    @Override
    public boolean needsRepack() {
        return false;
    }

    @Override
    public void markModified() {}

    public List<String> createBaseCurlCommand() {
        List<String> command = new ArrayList<>();
        command.add(curlPath);
        command.add("-u");
        command.add(options.getUsername() + ":" + options.getPassword());
        command.add("--ftp-pasv");
        command.add("-s"); // silent
        return command;
    }

    public String getParent(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return "/";
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) return "/";
        if (lastSlash == 0) return "/";
        return path.substring(0, lastSlash);
    }

    private String getName(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) return path;
        return path.substring(lastSlash + 1);
    }
}
