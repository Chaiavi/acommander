package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pack (7-Zip), Unpack (7-Zip) and Extract All (UniExtract) with the apps.json tools, on the file systems and paths
 * captured when the user started ({@link ClipboardTransfer#capture}). Archive and FTP sides go through local temp
 * copies. Blocking: each method returns once the result is in its target folder, so call it off the FX thread.
 */
public class ArchiveOperations {
    private static final Logger log = LoggerFactory.getLogger(ArchiveOperations.class);

    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public ArchiveOperations(AppRegistry registry, ExternalToolRunner runner) {
        this.registry = registry;
        this.runner = runner;
    }

    /** Packs {@code entries} into {@code archiveName} in {@code targetFolder}; non-local items are copied out under their own names first. */
    public void pack(VFileSystem sourceFs, List<Entry> entries, VFileSystem targetFs, String targetFolder, String archiveName)
            throws IOException {
        List<Path> tempPaths = new ArrayList<>();
        try {
            List<String> localPathsToPack = new ArrayList<>();
            Path stagingDir = null;
            for (Entry entry : entries) {
                if (sourceFs instanceof LocalFileSystem) {
                    localPathsToPack.add(entry.sourceInternalPath());
                    continue;
                }
                if (stagingDir == null) {
                    stagingDir = AppTempDir.createTempDirectory("acommander_pack_");
                    tempPaths.add(stagingDir);
                }
                Path staged = stagingDir.resolve(entry.name()).normalize();
                if (!stagingDir.equals(staged.getParent())) {
                    throw new IOException("Can't pack an item with this name: " + entry.name());
                }
                sourceFs.copy(entry.sourceInternalPath(), new LocalFileSystem(""), staged.toString());
                localPathsToPack.add(staged.toString());
            }
            if (localPathsToPack.isEmpty()) {
                log.info("No valid files to pack.");
                return;
            }

            boolean local = targetFs instanceof LocalFileSystem;
            Path localArchive = local ? Path.of(targetFolder, archiveName)
                    : AppTempDir.createTempDirectory("acommander_pack_target_").resolve(archiveName);
            if (!local) {
                tempPaths.add(localArchive.getParent());
            }
            ActionDefinition action = registry.requireAction("pack");
            runner.runExecutable(ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), null,
                    Map.of("${archiveFile}", localArchive.toString()), localPathsToPack), false).join();
            if (!local) {
                String target = ClipboardTransfer.targetInternalPath(targetFs, targetFolder, archiveName, false);
                try {
                    new LocalFileSystem("").copy(localArchive.toString(), targetFs, target);
                } catch (IOException e) {
                    throw new IOException("The archive was created but could not be uploaded to " + target, e);
                }
            }
        } finally {
            tempPaths.forEach(FileHelper::deleteQuietly);
        }
    }

    /** Unpacks a 7-Zip archive into {@code destinationFolder}; throws for a file 7-Zip can't unpack. */
    public void unpack(VFileSystem sourceFs, Entry archive, VFileSystem targetFs, String destinationFolder) throws IOException {
        if (archive.directory() || !ArchiveMode.isUnpackable(FileItem.extension(archive.name()))) {
            log.warn("Unpack rejected file '{}': not a supported archive format", archive.name());
            throw new IllegalArgumentException("The selected file is not a supported archive: " + archive.name());
        }
        unpackWith("unpack", sourceFs, archive, targetFs, destinationFolder);
    }

    /** Extracts anything UniExtract can open (installers, archives, …) into {@code destinationFolder}. */
    public void extractAll(VFileSystem sourceFs, Entry file, VFileSystem targetFs, String destinationFolder) throws IOException {
        unpackWith("extractAll", sourceFs, file, targetFs, destinationFolder);
    }

    /** Unpack and Extract All differ only in the tool; remote sources and targets go through local temp copies. */
    private void unpackWith(String actionId, VFileSystem sourceFs, Entry archive, VFileSystem targetFs,
                            String destinationFolder) throws IOException {
        List<Path> tempPaths = new ArrayList<>();
        try {
            Path archiveToUnpack = Path.of(archive.sourceInternalPath());
            if (!(sourceFs instanceof LocalFileSystem)) {
                archiveToUnpack = AppTempDir.createTempFile("acommander_unpack_", "_" + archive.name());
                tempPaths.add(archiveToUnpack);
                sourceFs.copy(archive.sourceInternalPath(), new LocalFileSystem(""), archiveToUnpack.toString());
            }
            boolean local = targetFs instanceof LocalFileSystem;
            Path localDest = local ? Path.of(destinationFolder) : AppTempDir.createTempDirectory("acommander_unpack_dest_");
            if (!local) {
                tempPaths.add(localDest);
            }

            ActionDefinition action = registry.requireAction(actionId);
            runner.runExecutable(ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), null,
                    Map.of("${destinationPath}", localDest.toString()),
                    List.of(archiveToUnpack.toAbsolutePath().toString())), false).join();
            if (!local) {
                try (var unpacked = java.nio.file.Files.list(localDest)) {
                    for (Path item : unpacked.toList()) {
                        String name = item.getFileName().toString();
                        new LocalFileSystem("").copy(item.toString(), targetFs, ClipboardTransfer.targetInternalPath(
                                targetFs, destinationFolder, name, java.nio.file.Files.isDirectory(item)));
                    }
                } catch (IOException e) {
                    throw new IOException("The files were unpacked but could not be uploaded to " + destinationFolder, e);
                }
            }
            log.debug("{} done for: {}", actionId, archive.name());
        } finally {
            tempPaths.forEach(FileHelper::deleteQuietly);
        }
    }
}
