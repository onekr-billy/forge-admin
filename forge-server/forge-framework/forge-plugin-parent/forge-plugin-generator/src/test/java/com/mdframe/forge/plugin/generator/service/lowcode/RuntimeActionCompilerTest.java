package com.mdframe.forge.plugin.generator.service.lowcode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeActionCompilerTest {

    @Test
    void treeRowsKeepDefaultOrderAndDoNotDuplicateBuiltInKeys() {
        Map<String, Object> tableProps = Map.of("customActions", List.of(
                Map.of("position", "row", "key", "edit", "label", "替代编辑"),
                Map.of("position", "row", "key", "approve", "label", "审批")));

        List<Map<String, Object>> actions = RuntimeActionCompiler.rowActions(tableProps, true);

        assertEquals(List.of("edit", "detail", "addChild", "delete", "approve"),
                actions.stream().map(action -> action.get("key")).toList());
        assertEquals("编辑", actions.get(0).get("label"));
        assertEquals("route", actions.get(4).get("actionType"));
    }

    @Test
    void positionAndPermissionFallbackPreserveCommandProtocol() {
        Map<String, Object> tableProps = Map.of("customActions", List.of(
                Map.of("position", "toolbar", "key", "export_all", "label", "导出"),
                Map.of("position", "row", "key", "submit", "label", "提交",
                        "actionType", "COMMAND", "permissionCode", "order:submit",
                        "actionConfig", Map.of("workflow", "approval"),
                        "params", List.of(
                                Map.of("name", "recordId", "sourceType", "ROW", "sourceField", "id"),
                                Map.of("target", "ignored")))));

        List<Map<String, Object>> row = RuntimeActionCompiler.customActions(tableProps, "row");

        assertEquals(1, row.size());
        assertEquals("submit", row.get(0).get("key"));
        assertEquals("COMMAND", row.get(0).get("actionType"));
        assertEquals("order:submit", row.get(0).get("permissionKey"));
        assertEquals("_self", row.get(0).get("openTarget"));
        assertEquals(Map.of("workflow", "approval"), row.get(0).get("actionConfig"));
        assertEquals(List.of(Map.of("name", "recordId", "sourceType", "ROW",
                "sourceField", "id", "value", "")), row.get(0).get("params"));
        assertEquals(1, RuntimeActionCompiler.customActions(tableProps, "toolbar").size());
        assertFalse(row.get(0).containsKey("routePath"));
    }
}
