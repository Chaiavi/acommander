package org.chaiware.acommander.tools;

import org.chaiware.acommander.helpers.AppTempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the Windows Properties dialog of a file or folder (Shell.Application through wscript). */
public final class FilePropertiesLauncher {

    // ponytail: wscript sleeps forever to keep the dialog open, so each call leaves one wscript.exe until logoff;
    // a window-close check in the script is the upgrade path (Phase 10.7).
    private static final String SCRIPT = """
            Set shell = CreateObject("Shell.Application")
            Set fso = CreateObject("Scripting.FileSystemObject")

            filePath = WScript.Arguments(0)

            ' Exit codes
            ERR_NOT_FOUND = 1
            ERR_NAMESPACE = 2
            ERR_ITEM = 3

            If Not fso.FileExists(filePath) And Not fso.FolderExists(filePath) Then
                WScript.Quit ERR_NOT_FOUND
            End If

            parentPath = fso.GetParentFolderName(filePath)
            itemName = fso.GetFileName(filePath)

            Set folder = shell.Namespace(parentPath)
            If folder Is Nothing Then
                WScript.Quit ERR_NAMESPACE
            End If

            Set item = folder.ParseName(itemName)
            If item Is Nothing Then
                WScript.Quit ERR_ITEM
            End If

            item.InvokeVerb "Properties"

            ' Keep script alive so properties window stays open
            Do
                WScript.Sleep 1000
            Loop
            """;

    private FilePropertiesLauncher() {
    }

    public static void open(Path target) throws IOException {
        Path script = AppTempDir.createTempFile("properties_", ".vbs");
        Files.writeString(script, SCRIPT.replace("\n", "\r\n"));
        ProcessRunner.of("wscript.exe", "//Nologo", script.toString(), target.toString()).launch();
    }
}
