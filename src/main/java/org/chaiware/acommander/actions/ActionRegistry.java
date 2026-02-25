package org.chaiware.acommander.actions;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.ActionScope;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AudioConversionSupport;
import org.chaiware.acommander.helpers.ImageConversionSupport;
import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.stream.Collectors;

public class ActionRegistry {
    private final List<AppAction> actions;

    public ActionRegistry(AppRegistry appRegistry, ActionExecutor executor) {
        actions = appRegistry.actionsForScope(ActionScope.COMMAND_PALETTE).stream()
                .map(action -> toAppAction(action, executor))
                .collect(Collectors.toList());
    }

    private AppAction toAppAction(ActionDefinition action, ActionExecutor executor) {
        SelectionRule rule = SelectionRule.fromString(action.getSelection());
        String builtin = action.getBuiltin() == null ? action.getId() : action.getBuiltin();
        return new AppAction(
                action.getId(),
                action.getLabel(),
                action.getShortcut(),
                action.getAliases(),
                ctx -> rule.isSatisfied(ctx.commander().filesPanesHelper.getSelectedItems())
                        && isSelectionAllowedForBuiltin(builtin, ctx),
                ctx -> executor.execute(action)
        );
    }

    private boolean isSelectionAllowedForBuiltin(String builtin, ActionContext ctx) {
        if (!"convertGraphicsFiles".equals(builtin)
                && !"convertAudioFiles".equals(builtin)
                && !"convertMediaFile".equals(builtin)) {
            return true;
        }
        if (ctx == null || ctx.commander() == null || ctx.commander().filesPanesHelper == null) {
            return false;
        }
        if ("convertMediaFile".equals(builtin)) {
            List<FileItem> selectedItems = ctx.commander().filesPanesHelper.getSelectedItems();
            return ImageConversionSupport.areAllConvertibleImages(selectedItems)
                    || AudioConversionSupport.areAllConvertibleAudio(selectedItems);
        }
        if ("convertGraphicsFiles".equals(builtin)) {
            return ImageConversionSupport.areAllConvertibleImages(ctx.commander().filesPanesHelper.getSelectedItems());
        }
        return AudioConversionSupport.areAllConvertibleAudio(ctx.commander().filesPanesHelper.getSelectedItems());
    }

    public List<AppAction> all() {
        return actions;
    }
}
