package org.chaiware.acommander.tools;

import org.chaiware.acommander.helpers.AppTempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the Windows Properties dialog of a file or folder (Shell.Application through wscript). */
public final class FilePropertiesLauncher {

    // ponytail: the dialog lives in wscript's process, so each open dialog keeps one idle wscript.exe (a WMI check
    // every 2 s) until the app exits; showing it in-process (ShellExecuteEx) would need JNA.
    private static final String SCRIPT = """
            Set shell = CreateObject("Shell.Application")
            Set fso = CreateObject("Scripting.FileSystemObject")

            filePath = WScript.Arguments(0)
            appPid = CLng(WScript.Arguments(1))

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

            ' Keep script alive so properties window stays open, until ACommander exits
            Set wmi = GetObject("winmgmts:\\\\.\\root\\cimv2")
            Do While wmi.ExecQuery("select ProcessId from Win32_Process where ProcessId = " & appPid).Count > 0
                WScript.Sleep 2000
            Loop
            """;

    private FilePropertiesLauncher() {
    }

    public static void open(Path target) throws IOException {
        Path script = AppTempDir.createTempFile("properties_", ".vbs");
        Files.writeString(script, SCRIPT.replace("\n", "\r\n"));
        String appPid = String.valueOf(ProcessHandle.current().pid());
        ProcessRunner.of("wscript.exe", "//Nologo", script.toString(), target.toString(), appPid).launch();
    }
}
