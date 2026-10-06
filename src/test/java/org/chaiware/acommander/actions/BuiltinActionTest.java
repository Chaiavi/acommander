package org.chaiware.acommander.actions;

import org.chaiware.acommander.config.AppConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinActionTest {

    @Test
    void everyBuiltinActionInAppsJsonHasAHandler() throws IOException {
        List<String> unknown = builtinIdsInAppsJson().stream()
                .filter(id -> BuiltinAction.fromId(id).isEmpty())
                .toList();

        assertThat(unknown).as("apps.json builtins missing from BuiltinAction").isEmpty();
    }

    @Test
    void everyBuiltinActionIsUsedByAppsJson() throws IOException {
        Set<String> used = builtinIdsInAppsJson();
        List<String> unused = Arrays.stream(BuiltinAction.values())
                .map(BuiltinAction::id)
                .filter(id -> !used.contains(id))
                .toList();

        assertThat(unused).as("BuiltinAction values no apps.json action reaches").isEmpty();
    }

    private static Set<String> builtinIdsInAppsJson() throws IOException {
        return new AppConfigLoader().load(Path.of("config", "apps.json")).getActions().stream()
                .filter(action -> !"external".equalsIgnoreCase(action.getType()))
                .map(action -> action.getBuiltin() == null ? action.getId() : action.getBuiltin())
                .collect(Collectors.toSet());
    }
}
