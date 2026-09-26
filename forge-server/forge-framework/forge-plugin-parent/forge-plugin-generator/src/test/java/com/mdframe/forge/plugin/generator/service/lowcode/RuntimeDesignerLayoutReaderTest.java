package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDesignerLayoutReaderTest {

    @Test
    void aiCrudSearchReadsSearchSettingsInsteadOfTableSettings() {
        LowcodePageSchema page = new LowcodePageSchema();
        page.setListGridLayout(Map.of("items", List.of(Map.of(
                "blockType", "AiCrudPage",
                "props", Map.of(
                        "fieldSettings", Map.of("name", Map.of("placeholder", "table")),
                        "searchFieldSettings", Map.of("name", Map.of("placeholder", "search")))))));

        assertEquals("search", RuntimeDesignerLayoutReader.resolveGridFieldSetting(page, "search", "name")
                .get("placeholder"));
        assertEquals("table", RuntimeDesignerLayoutReader.resolveGridFieldSetting(page, "table", "name")
                .get("placeholder"));
    }

    @Test
    void tableGlobalAlignmentFillsMissingOrInvalidFieldAlignment() {
        LowcodePageSchema page = new LowcodePageSchema();
        page.setListGridLayout(Map.of("items", List.of(Map.of(
                "blockType", "data-table",
                "props", Map.of(
                        "globalAlign", "center",
                        "fieldSettings", Map.of("name", Map.of("textAlign", "invalid")))))));

        assertEquals("center", RuntimeDesignerLayoutReader.resolveGridFieldSetting(page, "table", "name")
                .get("align"));
        assertEquals("center", RuntimeDesignerLayoutReader.resolveGridFieldSetting(page, "table", "other")
                .get("align"));
    }

    @Test
    void nestedFormRulesAndCanvasItemsAreReadWithoutMutation() {
        LowcodePageZone edit = new LowcodePageZone();
        edit.setZoneKey("edit");
        Map<String, Object> child = Map.of("field", "child");
        edit.setProps(Map.of(
                "formCreateRule", List.of(Map.of("field", "parent", "children", List.of(child))),
                "canvas", Map.of("items", List.of(Map.of("fieldRef", "child", "w", 280), "ignored"))));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setZones(List.of(edit));

        assertEquals(List.of("parent", "child"), RuntimeDesignerLayoutReader.extractFormRules(page)
                .stream().map(rule -> rule.get("field")).toList());
        assertEquals(1, RuntimeDesignerLayoutReader.extractCanvasItems(edit).size());
        assertEquals("child", RuntimeDesignerLayoutReader.extractCanvasItems(edit).get(0).get("fieldRef"));
        assertTrue(RuntimeDesignerLayoutReader.extractFormRules(new LowcodePageSchema()).isEmpty());
    }
}
