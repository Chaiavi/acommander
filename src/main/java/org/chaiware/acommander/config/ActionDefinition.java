package org.chaiware.acommander.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ActionDefinition {
    private String id;
    private String label;
    private String description;
    private String category;
    private String shortcut;
    private List<String> aliases = new ArrayList<>();
    private List<String> contexts = new ArrayList<>();
    private String selection = "none";
    private String type = "builtin";
    private String builtin;
    private String path;
    private List<String> args = new ArrayList<>();
    private Boolean refreshAfter;
    private PromptDefinition prompt;
    private Integer priority;
    private List<PriorityRuleDefinition> priorityRules = new ArrayList<>();
    private boolean ftp;
    private WriteTarget writes = WriteTarget.NONE;
    private List<FileType> fileTypes = new ArrayList<>();
    private List<Requirement> requires = new ArrayList<>();

    /** The pane an action writes to; that pane being read-only (a read-only archive) blocks the action. */
    public enum WriteTarget {
        @JsonProperty("none") NONE,
        @JsonProperty("source") SOURCE,
        @JsonProperty("target") TARGET,
        @JsonProperty("both") BOTH
    }

    /** The Command Palette offers the action only when every selected item is of one listed type. */
    public enum FileType {
        @JsonProperty("convertibleImage") CONVERTIBLE_IMAGE,
        @JsonProperty("convertibleAudio") CONVERTIBLE_AUDIO,
        @JsonProperty("convertibleVideo") CONVERTIBLE_VIDEO,
        @JsonProperty("media") MEDIA,
        @JsonProperty("imageWithMetadata") IMAGE_WITH_METADATA,
        @JsonProperty("videoWithMetadata") VIDEO_WITH_METADATA,
        @JsonProperty("audioWithMetadata") AUDIO_WITH_METADATA,
        @JsonProperty("executable") EXECUTABLE,
        @JsonProperty("archive") ARCHIVE,
        @JsonProperty("pdf") PDF
    }

    /** Extra conditions the Command Palette checks before offering the action. */
    public enum Requirement {
        @JsonProperty("clipboardHasFiles") CLIPBOARD_HAS_FILES,
        @JsonProperty("focusedPaneIsFtp") FOCUSED_PANE_IS_FTP,
        @JsonProperty("textFileInEachPane") TEXT_FILE_IN_EACH_PANE,
        @JsonProperty("navigationLinked") NAVIGATION_LINKED,
        @JsonProperty("navigationUnlinked") NAVIGATION_UNLINKED
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getShortcut() {
        return shortcut;
    }

    public void setShortcut(String shortcut) {
        this.shortcut = shortcut;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>() : aliases;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public void setContexts(List<String> contexts) {
        this.contexts = contexts == null ? new ArrayList<>() : contexts;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection == null ? "none" : selection;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? "builtin" : type;
    }

    public String getBuiltin() {
        return builtin;
    }

    public void setBuiltin(String builtin) {
        this.builtin = builtin;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args == null ? new ArrayList<>() : args;
    }

    public Boolean getRefreshAfter() {
        return refreshAfter;
    }

    public void setRefreshAfter(Boolean refreshAfter) {
        this.refreshAfter = refreshAfter;
    }

    public PromptDefinition getPrompt() {
        return prompt;
    }

    public void setPrompt(PromptDefinition prompt) {
        this.prompt = prompt;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public List<PriorityRuleDefinition> getPriorityRules() {
        return priorityRules;
    }

    public void setPriorityRules(List<PriorityRuleDefinition> priorityRules) {
        this.priorityRules = priorityRules == null ? new ArrayList<>() : priorityRules;
    }

    public boolean isFtp() {
        return ftp;
    }

    public void setFtp(boolean ftp) {
        this.ftp = ftp;
    }

    public WriteTarget getWrites() {
        return writes;
    }

    public void setWrites(WriteTarget writes) {
        this.writes = writes == null ? WriteTarget.NONE : writes;
    }

    public List<FileType> getFileTypes() {
        return fileTypes;
    }

    public void setFileTypes(List<FileType> fileTypes) {
        this.fileTypes = fileTypes == null ? new ArrayList<>() : fileTypes;
    }

    public List<Requirement> getRequires() {
        return requires;
    }

    public void setRequires(List<Requirement> requires) {
        this.requires = requires == null ? new ArrayList<>() : requires;
    }
}
