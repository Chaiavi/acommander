package org.chaiware.acommander.config;

import java.util.ArrayList;
import java.util.List;

public class PriorityRuleDefinition {
    private Integer score;
    private List<String> extensions = new ArrayList<>();
    private String extensionMatch = "all";
    private String selection;
    private Boolean clipboardNotEmpty;

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : extensions;
    }

    public String getExtensionMatch() {
        return extensionMatch;
    }

    public void setExtensionMatch(String extensionMatch) {
        this.extensionMatch = extensionMatch == null ? "all" : extensionMatch;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    public Boolean getClipboardNotEmpty() {
        return clipboardNotEmpty;
    }

    public void setClipboardNotEmpty(Boolean clipboardNotEmpty) {
        this.clipboardNotEmpty = clipboardNotEmpty;
    }
}
