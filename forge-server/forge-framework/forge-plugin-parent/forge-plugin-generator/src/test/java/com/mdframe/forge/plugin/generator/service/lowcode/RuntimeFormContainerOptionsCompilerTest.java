package com.mdframe.forge.plugin.generator.service.lowcode;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeFormContainerOptionsCompilerTest {

    @Test
    void editWidthOverridesCrudWidthAndDefault() {
        assertEquals("1080px", RuntimeFormContainerOptionsCompiler.defaultModalWidth(true, 1));
        assertEquals("1180px", RuntimeFormContainerOptionsCompiler.defaultModalWidth(false, 3));
        assertEquals("720px", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(
                Map.of("modalWidth", 720), Map.of("modalWidth", "980px"), "800px"));
        assertEquals("980px", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(
                Map.of(), Map.of("modalWidth", "980px"), "800px"));
    }

    @Test
    void defaultCrudWidthYieldsToFormStyleButRemainsLastFallback() {
        assertEquals("1080px", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(
                Map.of("modalWidth", "auto", "editFormStyle", Map.of("maxWidth", "1080px", "width", "100%")),
                Map.of("modalWidth", "900px"), "800px"));
        assertEquals("900px", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(
                Map.of(), Map.of("modalWidth", "900px"), "800px"));
        assertEquals("800px", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(
                Map.of(), Map.of("modalWidth", "100%"), "800px"));
    }

    @Test
    void modesAndWorkspaceDefaultsKeepRuntimeProtocol() {
        assertEquals("tabWorkspace", RuntimeFormContainerOptionsCompiler.formOpenMode("TABWORKSPACE"));
        assertEquals("flat", RuntimeFormContainerOptionsCompiler.formOpenMode("FLAT"));
        assertEquals("modal", RuntimeFormContainerOptionsCompiler.formOpenMode("unknown"));
        assertEquals("drawer", RuntimeFormContainerOptionsCompiler.modalType("DRAWER"));
        assertEquals("modal", RuntimeFormContainerOptionsCompiler.modalType("flat"));
        assertEquals(Map.of("maxTabs", 1, "reuseRecordTab", false, "closeAfterSave", false, "showDirtyMark", true),
                RuntimeFormContainerOptionsCompiler.tabWorkspaceOptions(
                        Map.of("tabWorkspace", Map.of("maxTabs", 5, "reuseRecordTab", true)),
                        Map.of("tabWorkspace", Map.of("maxTabs", 0, "reuseRecordTab", false))));
    }
}
