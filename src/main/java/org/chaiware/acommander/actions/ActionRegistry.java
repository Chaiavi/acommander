package org.chaiware.acommander.actions;

import java.util.List;

public class ActionRegistry {
    private final List<AppAction> actions;

    public ActionRegistry() {
        actions = List.of(
                new AppAction("help", "Help", "F1", List.of("manual", "docs"), ctx -> true, ctx -> ctx.commander().help()),
                new AppAction("rename", "Rename", "F2", List.of("mvname"), ctx -> true, ctx -> ctx.commander().renameFile()),
                new AppAction("view", "View", "F3", List.of("preview"), ctx -> true, ctx -> ctx.commander().viewFile()),
                new AppAction("edit", "Edit", "F4", List.of("notepad"), ctx -> true, ctx -> ctx.commander().editFile()),
                new AppAction("copy", "Copy", "F5", List.of("cp"), ctx -> true, ctx -> ctx.commander().copyFile()),
                new AppAction("move", "Move", "F6", List.of("mv"), ctx -> true, ctx -> ctx.commander().moveFile()),
                new AppAction("mkdir", "Create Directory", "F7", List.of("new folder", "md"), ctx -> true, ctx -> ctx.commander().makeDirectory()),
                new AppAction("mkfile", "Create File", "Alt+F7", List.of("new file", "touch"), ctx -> true, ctx -> ctx.commander().makeFile()),
                new AppAction("delete", "Delete", "F8", List.of("remove", "rm", "del"), ctx -> true, ctx -> ctx.commander().deleteFile()),
                new AppAction("terminal", "Open Terminal Here", "F9", List.of("shell", "cmd", "powershell"), ctx -> true, ctx -> ctx.commander().terminalHere()),
                new AppAction("explorer", "Open Explorer Here", "Alt+F9", List.of("open folder"), ctx -> true, ctx -> ctx.commander().explorerHere()),
                new AppAction("search", "Search Files", "F10", List.of("find", "grep"), ctx -> true, ctx -> ctx.commander().search()),
                new AppAction("pack", "Pack to Zip", "F11", List.of("zip", "archive"), ctx -> true, ctx -> ctx.commander().pack()),
                new AppAction("unpack", "Unpack", "F12", List.of("extract", "unzip"), ctx -> true, ctx -> ctx.commander().unpackFile()),
                new AppAction("extractAll", "Extract All", "Alt+F12", List.of("force extract"), ctx -> true, ctx -> ctx.commander().extractAll()),
                new AppAction("mergePdf", "Merge PDF Files", "Shift+F1", List.of("pdf merge"), ctx -> true, ctx -> ctx.commander().mergePDFFiles()),
                new AppAction("extractPdfPages", "Extract PDF Pages", "Shift+F2", List.of("pdf split"), ctx -> true, ctx -> ctx.commander().extractPDFPages()),
                new AppAction("refresh", "Refresh Panels", "Ctrl+R", List.of("reload"), ctx -> true, ctx -> ctx.commander().filesPanesHelper.refreshFileListViews())
        );
    }

    public List<AppAction> all() {
        return actions;
    }
}
