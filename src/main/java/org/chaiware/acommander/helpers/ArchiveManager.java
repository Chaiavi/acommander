package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages archive sessions including extraction to temp folders and repacking.
 * For read-write archives, extracts to temp folder and syncs changes back.
 * For read-only archives, provides read-only access.
 */
public class ArchiveManager {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveManager.class);
    private static final String SEVEN_Z_PATH = BundledTool.SEVEN_ZIP.path().toString();
    private static final DateTimeFormatter RECOVERY_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
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
        String extension = FileItem.extension(Paths.get(archivePath).getFileName().toString());
        if (!ArchiveMode.isSupportedExtension(extension)) {
            throw new IOException("Unsupported archive file type: " + archivePath);
        }
        ArchiveMode mode = ArchiveMode.fromExtension(extension);
        
        // Create temp folder
        Path tempFolder = AppTempDir.createTempDirectory("acommander_archive_");
        
        // Extract entire archive to temp folder
        extractArchive(archivePath, tempFolder);
        
        ArchiveSession session = new ArchiveSession(archivePath, tempFolder, mode);
        logger.info("Archive opened in {} mode: {}", mode.name(), archivePath);
        
        return session;
    }
    
    /**
     * Closes an archive session: repacks a changed read-write archive, then deletes the extracted folder.
     * If the repack fails, the edited files are copied out first and the exception says where.
     */
    public void closeArchive(ArchiveSession session) throws IOException {
        logger.info("Closing archive session: {}", session.getArchivePath());
        Path tempFolder = session.getTempFolder();
        if (session.getMode() == ArchiveMode.READ_WRITE && session.isNeedsRepack()) {
            try {
                repackArchive(session);
            } catch (IOException repackError) {
                Path archive = Paths.get(session.getArchivePath());
                Path recovered;
                try {
                    recovered = recoverEdits(tempFolder, archive, LocalDateTime.now().format(RECOVERY_STAMP));
                } catch (IOException recoveryError) {
                    repackError.addSuppressed(recoveryError);
                    throw new IOException("Changes could not be saved into " + archive.getFileName()
                            + " and could not be copied out. Copy them from " + tempFolder
                            + " before closing ACommander.", repackError);
                }
                FileHelper.deleteQuietly(tempFolder);
                throw new IOException("Changes could not be saved into " + archive.getFileName()
                        + ". Your edited files were copied to " + recovered, repackError);
            }
        }
        FileHelper.deleteQuietly(tempFolder);
    }

    /** Copies the edited files next to the archive (or to the home folder) as {@code <archive>.recovered-<stamp>}. */
    static Path recoverEdits(Path tempFolder, Path archive, String stamp) throws IOException {
        String name = archive.getFileName() + ".recovered-" + stamp;
        List<Path> targets = new ArrayList<>();
        if (archive.getParent() != null) {
            targets.add(archive.getParent().resolve(name));
        }
        targets.add(Paths.get(System.getProperty("user.home")).resolve(name));
        IOException lastError = null;
        for (Path target : targets) {
            try {
                FileHelper.copyTree(tempFolder, target);
                logger.warn("Repack failed; edited files copied to {}", target);
                return target;
            } catch (IOException e) {
                lastError = e;
                FileHelper.deleteQuietly(target);
            }
        }
        throw lastError;
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
        String extension = "." + FileItem.extension(archiveFile.getFileName().toString());
        
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
     * Executes a 7z command and throws IOException on failure.
     */
    private void execute7zCommand(List<String> command, String operation) throws IOException {
        logger.debug("Running 7z {}: {}", operation, String.join(" ", command));
        
        try {
            ProcessRunner.Result result = ProcessRunner.of(command).mergeStderr().run();
            result.stdout().forEach(line -> logger.trace("7z: {}", line));
            int exitCode = result.exitCode();
            if (exitCode != 0) {
                throw new IOException("7z " + operation + " failed with exit code " + exitCode);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("7z " + operation + " interrupted", e);
        }
    }
}
