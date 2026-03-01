package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
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
import java.util.List;

/**
 * Manages archive sessions including extraction to temp folders and repacking.
 * For read-write archives, extracts to temp folder and syncs changes back.
 * For read-only archives, provides read-only access.
 */
public class ArchiveManager {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveManager.class);
    private static final String SEVEN_Z_PATH = Paths.get(System.getProperty("user.dir"), "apps", "extract_all", "UniExtract", "bin", "x64", "7z.exe").toString();
    
    /**
     * Opens an archive and creates a session.
     * For read-write archives, extracts to a temp folder.
     * For read-only archives, also extracts to temp folder but marks as read-only.
     * 
     * @param archivePath Path to the archive file
     * @return ArchiveSession for managing the archive access
     * @throws IOException If extraction fails
     */
    public ArchiveSession openArchive(String archivePath) throws IOException {
        logger.info("Opening archive: {}", archivePath);
        
        // Determine the archive mode based on extension
        String extension = getFileExtension(archivePath);
        ArchiveMode mode = ArchiveMode.fromExtension(extension);
        
        // Create temp folder
        Path tempFolder = Files.createTempDirectory("acommander_archive_");
        tempFolder.toFile().deleteOnExit();
        
        // Extract entire archive to temp folder
        extractArchive(archivePath, tempFolder);
        
        ArchiveSession session = new ArchiveSession(archivePath, tempFolder, mode);
        logger.info("Archive opened in {} mode: {}", mode.name(), archivePath);
        
        return session;
    }
    
    /**
     * Closes an archive session.
     * For read-write archives with changes, repacks the archive.
     * Cleans up the temp folder.
     * 
     * @param session The session to close
     * @throws IOException If repacking fails
     */
    public void closeArchive(ArchiveSession session) throws IOException {
        logger.info("Closing archive session: {}", session.getArchivePath());
        
        try {
            // For read-write archives with changes, repack the archive
            if (session.getMode() == ArchiveMode.READ_WRITE && session.isNeedsRepack()) {
                repackArchive(session);
            }
        } finally {
            // Always clean up temp folder
            session.cleanup();
        }
    }
    
    /**
     * Extracts an entire archive to a destination folder.
     */
    private void extractArchive(String archivePath, Path destFolder) throws IOException {
        logger.debug("Extracting archive: {} to: {}", archivePath, destFolder);
        
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_PATH);
        command.add("x");  // Extract with full paths
        command.add("-y");  // Assume Yes on all queries
        command.add("-o" + destFolder.toString());  // Output directory
        command.add(archivePath);
        
        execute7zCommand(command, "extract");
    }
    
    /**
     * Repacks an archive by updating it with all files from the temp folder.
     */
    private void repackArchive(ArchiveSession session) throws IOException {
        logger.info("Repacking archive: {}", session.getArchivePath());

        Path tempFolder = session.getTempFolder();
        String archivePath = session.getArchivePath();
        Path archiveFile = Paths.get(archivePath);
        Path parentDir = archiveFile.getParent();
        
        // Use the same extension as the original archive for the temp file
        String extension = getFileExtension(archivePath);
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        
        Path tempArchive = Files.createTempFile(parentDir != null ? parentDir : Paths.get("."), "repack_", extension);
        
        try {
            // Delete the empty file created by createTempFile to let 7z create it fresh
            Files.deleteIfExists(tempArchive);

            // Create new archive from temp folder content
            List<String> command = new ArrayList<>();
            command.add(SEVEN_Z_PATH);
            command.add("a");  // Add to archive
            command.add("-y");  // Assume Yes
            command.add(tempArchive.toString());
            
            // Using '.' and -r in the temp folder is safer for adding contents correctly
            command.add(tempFolder.toString() + "\\*");

            execute7zCommand(command, "repack-create");

            if (Files.exists(tempArchive)) {
                // Replace original archive with the new one
                Files.move(tempArchive, archiveFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("Archive repacked successfully: {}", archivePath);
            } else {
                throw new IOException("Failed to create temporary archive during repack");
            }
        } catch (IOException e) {
            logger.error("Repack failed for {}: {}", archivePath, e.getMessage());
            if (Files.exists(tempArchive)) {
                try {
                    Files.deleteIfExists(tempArchive);
                } catch (IOException cleanupEx) {
                    logger.warn("Failed to cleanup temp archive: {}", tempArchive);
                }
            }
            throw e;
        }
    }
    
    /**
     * Deletes an entry from an archive.
     * Used for cleaning up old filenames after rename operations.
     * 
     * @param archivePath Path to the archive file
     * @param entryPath Path inside the archive to delete
     * @throws IOException If the deletion fails
     */
    public void deleteEntryFromArchive(String archivePath, String entryPath) throws IOException {
        logger.debug("Deleting entry: {} from archive: {}", entryPath, archivePath);
        
        List<String> command = new ArrayList<>();
        command.add(SEVEN_Z_PATH);
        command.add("d");  // Delete entries from archive
        command.add("-y");  // Assume Yes on all queries
        command.add(archivePath);
        command.add(entryPath);
        
        execute7zCommand(command, "delete");
        
        logger.debug("Successfully deleted entry from archive: {}", entryPath);
    }
    
    /**
     * Executes a 7z command and throws IOException on failure.
     */
    private void execute7zCommand(List<String> command, String operation) throws IOException {
        logger.debug("Running 7z {}: {}", operation, String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // Capture output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.trace("7z: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("7z " + operation + " failed with exit code " + exitCode);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z " + operation + " interrupted", e);
        }
    }
    
    /**
     * Gets the file extension from a path.
     */
    private String getFileExtension(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    /**
     * Gets the path to the 7z executable.
     */
    public static String get7zPath() {
        return SEVEN_Z_PATH;
    }
}
