package org.chaiware.acommander.tools;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ToolCommandBuilder {
    private static final String SELECTED_FILE = "${selectedFile}";
    private static final String SELECTED_FILE_QUOTED = "${selectedFileQuoted}";
    private static final String SELECTED_FILES = "${selectedFiles}";
    private static final String SELECTED_FILES_JOINED = "${selectedFilesJoined}";
    private static final String FOCUSED_PATH = "${focusedPath}";
    private static final String FOCUSED_PATH_QUOTED = "${focusedPathQuoted}";
    private static final String TARGET_FOLDER = "${targetFolder}";
    private static final String TARGET_FOLDER_QUOTED = "${targetFolderQuoted}";
    private static final String SELECTED_NAME = "${selectedName}";

    private ToolCommandBuilder() {
    }

    public static List<String> buildCommand(
            String path,
            List<String> overrideArgs,
            FilesPanesHelper filesPanesHelper
    ) {
        return buildCommand(path, overrideArgs, filesPanesHelper, Collections.emptyMap(), null);
    }

    public static List<String> buildCommand(
            String path,
            List<String> overrideArgs,
            FilesPanesHelper filesPanesHelper,
            Map<String, String> extraValues,
            List<String> overrideSelectedFiles
    ) {
        String toolPath = resolvePath(path);
        if (toolPath == null || toolPath.isBlank()) {
            return List.of();
        }

        Map<String, String> values = buildValues(filesPanesHelper, extraValues, overrideSelectedFiles);

        List<String> args = overrideArgs == null ? List.of() : overrideArgs;
        List<String> command = new ArrayList<>();
        command.add(toolPath);
        for (String arg : args) {
            if (SELECTED_FILES.equals(arg)) {
                command.addAll(resolveSelectedFiles(filesPanesHelper, overrideSelectedFiles));
                continue;
            }
            command.add(replacePlaceholders(arg, values));
        }

        return command;
    }

    public static String resolveTemplate(
            String template,
            FilesPanesHelper filesPanesHelper,
            Map<String, String> extraValues,
            List<String> overrideSelectedFiles
    ) {
        if (template == null) {
            return null;
        }
        Map<String, String> values = buildValues(filesPanesHelper, extraValues, overrideSelectedFiles);
        return replacePlaceholders(template, values);
    }

    private static String resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (isBuiltinCommand(path)) {
            return path;
        }
        Path resolved = Paths.get(path);
        if (resolved.isAbsolute()) {
            return resolved.toString();
        }
        return Paths.get(System.getProperty("user.dir"), path).toString();
    }

    private static String replacePlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (var entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, String> buildValues(
            FilesPanesHelper filesPanesHelper,
            Map<String, String> extraValues,
            List<String> overrideSelectedFiles
    ) {
        List<String> selectedFiles = resolveSelectedFiles(filesPanesHelper, overrideSelectedFiles);
        String selectedFile = selectedFiles.isEmpty() ? "" : selectedFiles.getFirst();
        String focusedPath = filesPanesHelper == null ? "" : nullToEmpty(filesPanesHelper.getFocusedPath());
        String targetFolder = filesPanesHelper == null ? "" : nullToEmpty(filesPanesHelper.getUnfocusedPath());
        String selectedFilesJoined = selectedFiles.stream()
                .map(filePath -> "\"" + filePath + "\"")
                .collect(Collectors.joining(","));
        String selectedName = resolveSelectedName(filesPanesHelper, selectedFile);

        Map<String, String> values = new HashMap<>();
        values.put(SELECTED_FILE, selectedFile);
        values.put(SELECTED_FILE_QUOTED, quote(selectedFile));
        values.put(SELECTED_FILES_JOINED, selectedFilesJoined);
        values.put(FOCUSED_PATH, focusedPath);
        values.put(FOCUSED_PATH_QUOTED, quote(focusedPath));
        values.put(TARGET_FOLDER, targetFolder);
        values.put(TARGET_FOLDER_QUOTED, quote(targetFolder));
        values.put(SELECTED_NAME, selectedName);
        if (extraValues != null) {
            values.putAll(extraValues);
            addQuotedAliases(values, extraValues);
        }
        return values;
    }

    private static void addQuotedAliases(Map<String, String> values, Map<String, String> extraValues) {
        for (Map.Entry<String, String> entry : extraValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith("${") || !key.endsWith("}")) {
                continue;
            }
            String inner = key.substring(2, key.length() - 1);
            if (inner.endsWith("Quoted")) {
                continue;
            }
            String quotedAlias = "${" + inner + "Quoted}";
            values.putIfAbsent(quotedAlias, quote(entry.getValue()));
        }
    }

    private static List<String> resolveSelectedFiles(
            FilesPanesHelper filesPanesHelper,
            List<String> overrideSelectedFiles
    ) {
        if (overrideSelectedFiles != null) {
            return overrideSelectedFiles;
        }
        List<FileItem> selectedItems = filesPanesHelper == null
                ? List.of()
                : nullToEmptyList(filesPanesHelper.getSelectedItems());
        return selectedItems.stream()
                .filter(item -> !isParentFolder(item))
                .map(FileItem::getFullPath)
                .collect(Collectors.toList());
    }

    private static String resolveSelectedName(FilesPanesHelper filesPanesHelper, String selectedFile) {
        List<FileItem> selectedItems = filesPanesHelper == null
                ? List.of()
                : nullToEmptyList(filesPanesHelper.getSelectedItems());
        if (!selectedItems.isEmpty()) {
            return selectedItems.getFirst().getName();
        }
        if (selectedFile == null || selectedFile.isBlank()) {
            return "";
        }
        return Paths.get(selectedFile).getFileName().toString();
    }

    private static boolean isBuiltinCommand(String path) {
        if (path.contains("\\") || path.contains("/")) {
            return false;
        }
        if (path.matches("^[A-Za-z]:.*")) {
            return false;
        }
        return "powershell".equalsIgnoreCase(path)
                || "pwsh".equalsIgnoreCase(path)
                || "cmd".equalsIgnoreCase(path);
    }

    private static String quote(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<FileItem> nullToEmptyList(List<FileItem> value) {
        return value == null ? List.of() : value;
    }

    private static boolean isParentFolder(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }
}
