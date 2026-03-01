package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.VirtualFileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class for interacting with 7-Zip to list and extract archive contents.
 * Uses 7zG.exe for GUI operations or 7z.exe for command-line operations.
 */
public class ArchiveService {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveService.class);
    private static final String SEVEN_Z_PATH = Paths.get(System.getProperty("user.dir"), "apps", "pack_unpack", "7zG.exe").toString();
    private static final String SEVEN_Z_CONSOLE_PATH = Paths.get(System.getProperty("user.dir"), "apps", "extract_all", "UniExtract", "bin", "x64", "7z.exe").toString();
    
    // Supported archive extensions by 7-Zip
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
        "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "lzma", "cab",
        "iso", "img", "vhd", "wim", "swm", "esd", "fat", "ntfs", "vmdk", "qcow2",
        "arj", "chm", "cpio", "cramfs", "deb", "dmg", "elf", "ext", "gpt", "hfs",
        "ihex", "lzh", "lzma86", "mbr", "msi", "nsis", "palm", "pcap", "pe",
        "ppmd", "rpm", "squashfs", "uefi", "vdi", "xar", "z", "zipx"
    );
    
    /**
     * Checks if the given file extension is supported by 7-Zip.
     */
    public static boolean isSupportedArchiveExtension(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    /**
     * Lists the contents of an archive at the specified entry path.
     * 
     * @param archivePath Path to the archive file
     * @param entryPath Path inside the archive (empty string for root)
     * @return List of VirtualFileItem representing the contents
     * @throws IOException If listing fails
     */
    public List<VirtualFileItem> listArchiveContents(String archivePath, String entryPath) throws IOException {
        logger.debug("Listing archive contents: {} at path: {}", archivePath, entryPath);
        
        // Build the 7z command: 7z l -ba -slt <archive> <path>
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_CONSOLE_PATH);
        command.add("l");  // List contents
        command.add("-ba");  // Disable "Listing archive" header
        command.add("-slt");  // Show technical information
        command.add(archivePath);
        
        if (!entryPath.isEmpty()) {
            command.add(entryPath + "*");
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            List<String> output = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("7z command failed with exit code " + exitCode);
            }
            
            return parse7zListOutput(output, archivePath, entryPath);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z command interrupted", e);
        }
    }
    
    /**
     * Parses the output of 7z l -slt command to extract file entries.
     */
    private List<VirtualFileItem> parse7zListOutput(List<String> lines, String archivePath, String entryPath) {
        List<VirtualFileItem> items = new ArrayList<>();
        
        // Pattern to match 7z technical output
        // Example:
        // Path = folder/file.txt
        // Size = 1234
        // Packed Size = 567
        // Modified = 2024:01:15 10:30:45
        // Attr = ...
        // Method = Deflate
        
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            
            // Each file entry starts with "Path = "
            if (line.startsWith("Path = ")) {
                String fullPath = line.substring(7).trim();
                
                // Skip if this path doesn't start with our entry path
                if (!entryPath.isEmpty() && !fullPath.startsWith(entryPath)) {
                    i++;
                    continue;
                }
                
                // Get the relative name within the current entry path
                String relativePath;
                if (entryPath.isEmpty()) {
                    relativePath = fullPath;
                } else {
                    relativePath = fullPath.substring(entryPath.length());
                }
                
                // Skip the entry path itself (it's a directory marker)
                if (relativePath.isEmpty() || relativePath.equals("/")) {
                    i++;
                    continue;
                }
                
                // Remove leading slash if present
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                
                // Skip if this is a deeper nested entry (we only want immediate children)
                // Check if there's a slash after removing the entry path prefix
                int slashIndex = relativePath.indexOf('/');
                if (slashIndex >= 0) {
                    // This is a nested entry - extract just the directory name
                    String dirName = relativePath.substring(0, slashIndex);
                    
                    // Check if we already added this directory
                    boolean alreadyAdded = items.stream()
                        .anyMatch(item -> item.getEntryName().equals(dirName) && item.isDirectory());
                    
                    if (!alreadyAdded) {
                        items.add(new VirtualFileItem(
                            archivePath,
                            dirName,
                            entryPath.isEmpty() ? dirName : entryPath + "/" + dirName,
                            0,  // Size will be calculated if needed
                            0,
                            null,
                            true
                        ));
                    }
                    i++;
                    continue;
                }
                
                // Parse other attributes
                long size = 0;
                long packedSize = 0;
                Long modified = null;
                boolean isDir = false;
                
                // Look ahead for attributes
                int j = i + 1;
                while (j < lines.size() && !lines.get(j).trim().startsWith("Path = ")) {
                    String attrLine = lines.get(j).trim();
                    if (attrLine.startsWith("Size = ")) {
                        size = parseLong(attrLine.substring(7).trim());
                    } else if (attrLine.startsWith("Packed Size = ")) {
                        packedSize = parseLong(attrLine.substring(13).trim());
                    } else if (attrLine.startsWith("Modified = ")) {
                        modified = parse7zDate(attrLine.substring(10).trim());
                    } else if (attrLine.startsWith("Attr = ")) {
                        String attr = attrLine.substring(7).trim();
                        isDir = attr.contains("D");
                    }
                    j++;
                }
                
                String entryName = relativePath;
                items.add(new VirtualFileItem(
                    archivePath,
                    entryName,
                    fullPath,
                    size,
                    packedSize,
                    modified,
                    isDir
                ));
                
                i = j;
            } else {
                i++;
            }
        }
        
        return items;
    }
    
    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Parses 7z date format: "2024:01:15 10:30:45"
     */
    private Long parse7zDate(String dateStr) {
        try {
            // Format: YYYY:MM:DD HH:MM:SS
            String[] parts = dateStr.split(" ");
            if (parts.length != 2) {
                return null;
            }
            
            String[] dateParts = parts[0].split(":");
            String[] timeParts = parts[1].split(":");
            
            if (dateParts.length != 3 || timeParts.length != 3) {
                return null;
            }
            
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]) - 1; // Java months are 0-based
            int day = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            int second = Integer.parseInt(timeParts[2]);
            
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, day, hour, minute, second);
            return cal.getTimeInMillis();
            
        } catch (Exception e) {
            logger.debug("Failed to parse 7z date: {}", dateStr, e);
            return null;
        }
    }
    
    /**
     * Extracts a single entry from an archive to a temporary file.
     * 
     * @param archivePath Path to the archive file
     * @param entryPath Full path of the entry inside the archive
     * @param destDir Destination directory
     * @return Path to the extracted file
     * @throws IOException If extraction fails
     */
    public Path extractEntry(String archivePath, String entryPath, Path destDir) throws IOException {
        logger.debug("Extracting entry: {} from archive: {} to: {}", entryPath, archivePath, destDir);
        
        Files.createDirectories(destDir);
        
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_CONSOLE_PATH);
        command.add("e");  // Extract
        command.add("-y");  // Assume Yes on all queries
        command.add("-o" + destDir.toString());  // Output directory
        command.add(archivePath);
        command.add(entryPath);  // Specific entry to extract
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("7z: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("7z extract failed with exit code " + exitCode);
            }
            
            // Return the extracted file path
            String entryName = Paths.get(entryPath).getFileName().toString();
            Path extractedFile = destDir.resolve(entryName);
            
            if (!Files.exists(extractedFile)) {
                throw new IOException("Extracted file not found: " + extractedFile);
            }
            
            return extractedFile;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z extract interrupted", e);
        }
    }
    
    /**
     * Extracts an entry from an archive to a temporary file and returns the temp file.
     * The caller is responsible for deleting the temp file when done.
     */
    public Path extractEntryToTemp(String archivePath, String entryPath) throws IOException {
        Path tempDir = Files.createTempDirectory("acommander_archive_");
        tempDir.toFile().deleteOnExit();

        return extractEntry(archivePath, entryPath, tempDir);
    }
    
    /**
     * Updates a file inside an archive by adding the modified file back.
     * This uses 7z to update the archive entry with the modified file.
     * 
     * @param archivePath Path to the archive file
     * @param entryPath Path inside the archive to update
     * @param modifiedFile The modified file to put back into the archive
     * @throws IOException If the update fails
     */
    public void updateEntryInArchive(String archivePath, String entryPath, Path modifiedFile) throws IOException {
        logger.debug("Updating entry: {} in archive: {} with file: {}", entryPath, archivePath, modifiedFile);
        
        // Get just the filename from the entry path (e.g., "folder/file.txt" -> "file.txt")
        String entryName = Paths.get(entryPath).getFileName().toString();
        
        // Create a temp directory and copy the modified file there with just the entry name
        Path tempDir = Files.createTempDirectory("acommander_update_");
        tempDir.toFile().deleteOnExit();
        Path fileInTempDir = tempDir.resolve(entryName);
        Files.copy(modifiedFile, fileInTempDir, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_CONSOLE_PATH);
        command.add("u");  // Update - add files to archive
        command.add("-y");  // Assume Yes on all queries
        command.add(archivePath);
        command.add(entryName);  // Use just the filename, not full path
        
        // Run from the temp directory so 7z sees just the filename
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("7z: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("7z update failed with exit code " + exitCode);
            }
            
            logger.debug("Successfully updated entry in archive: {}", entryPath);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z update interrupted", e);
        } finally {
            // Clean up temp directory
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.debug("Failed to delete temp file: {}", path);
                        }
                    });
            } catch (IOException e) {
                logger.debug("Failed to clean up temp directory", e);
            }
        }
    }
    
    /**
     * Deletes an entry from an archive.
     * 
     * @param archivePath Path to the archive file
     * @param entryPath Path inside the archive to delete
     * @throws IOException If the deletion fails
     */
    public void deleteEntryFromArchive(String archivePath, String entryPath) throws IOException {
        logger.debug("Deleting entry: {} from archive: {}", entryPath, archivePath);
        
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_CONSOLE_PATH);
        command.add("d");  // Delete entries from archive
        command.add("-y");  // Assume Yes on all queries
        command.add(archivePath);
        command.add(entryPath);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("7z: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("7z delete failed with exit code " + exitCode);
            }
            
            logger.debug("Successfully deleted entry from archive: {}", entryPath);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z delete interrupted", e);
        }
    }

    /**
     * Gets the path to the 7z executable.
     */
    public static String get7zPath() {
        return SEVEN_Z_CONSOLE_PATH;
    }
}
