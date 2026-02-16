package org.chaiware.acommander.config;

import java.util.ArrayList;
import java.util.List;

public class ActionDefinition {
    private String id;
    private String label;
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
}
