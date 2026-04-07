package org.chaiware.acommander.actions;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.PriorityRuleDefinition;
import org.chaiware.acommander.model.FileItem;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ActionPriorityEngine {

    public int priority(ActionDefinition action, ActionContext context) {
        if (action == null) {
            return 0;
        }
        int total = action.getPriority() == null ? 0 : action.getPriority();
        for (PriorityRuleDefinition rule : action.getPriorityRules()) {
            if (matches(rule, context)) {
                total += rule.getScore() == null ? 0 : rule.getScore();
            }
        }
        return total;
    }

    private boolean matches(PriorityRuleDefinition rule, ActionContext context) {
        if (rule == null) {
            return false;
        }

        List<FileItem> selected = selectedItems(context);
        if (rule.getSelection() != null && !rule.getSelection().isBlank()) {
            SelectionRule selectionRule = SelectionRule.fromString(rule.getSelection());
            if (!selectionRule.isSatisfied(selected)) {
                return false;
            }
        }

        if (rule.getClipboardNotEmpty() != null) {
            boolean hasClipboard = context != null
                    && context.commander() != null
                    && context.commander().hasClipboardTransferEntries();
            if (rule.getClipboardNotEmpty() != hasClipboard) {
                return false;
            }
        }

        if (!rule.getExtensions().isEmpty()) {
            Set<String> wanted = rule.getExtensions().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            if (wanted.isEmpty()) {
                return false;
            }

            List<String> selectedExtensions = selected.stream()
                    .filter(item -> item != null && !item.isDirectory())
                    .map(this::normalizedExtension)
                    .filter(ext -> !ext.isBlank())
                    .toList();

            if (selectedExtensions.isEmpty()) {
                return false;
            }

            String mode = rule.getExtensionMatch() == null ? "all" : rule.getExtensionMatch().toLowerCase(Locale.ROOT);
            if ("any".equals(mode)) {
                if (selectedExtensions.stream().noneMatch(wanted::contains)) {
                    return false;
                }
            } else {
                if (selectedExtensions.stream().anyMatch(ext -> !wanted.contains(ext))) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<FileItem> selectedItems(ActionContext context) {
        if (context == null || context.commander() == null || context.commander().filesPanesHelper == null) {
            return Collections.emptyList();
        }
        List<FileItem> selected = context.commander().filesPanesHelper.getSelectedItems();
        return selected == null ? Collections.emptyList() : selected;
    }

    private String normalizedExtension(FileItem item) {
        String name = item == null ? "" : item.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
