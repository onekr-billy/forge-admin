package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessProcessRuntimeActionCompiler")
class BusinessProcessRuntimeActionCompilerTest {

    private final BusinessProcessRuntimeActionCompiler compiler =
            new BusinessProcessRuntimeActionCompiler(new ObjectMapper());

    @Test
    @DisplayName("manual start with default positions compiles a row START_PROCESS action")
    void compileManualStartRowAction() {
        BusinessProcessSchema schema = schema("START_MANUAL", Map.of(
                "positions", List.of("ROW", "DETAIL"),
                "permission", "ai:businessProcess:start",
                "confirmText", "确认提交当前记录？"));

        List<Map<String, Object>> actions = compiler.compileSchema(schema, "CRM_APP", "submit_approval", "提交审批");

        assertEquals(1, actions.size());
        Map<String, Object> action = actions.get(0);
        assertEquals("startProcess:submit_approval", action.get("key"));
        assertEquals("START_PROCESS", action.get("actionType"));
        assertEquals("row", action.get("position"));
        assertEquals("CRM_APP", action.get("applicationCode"));
        assertEquals("submit_approval", action.get("processCode"));
        assertEquals("order", action.get("objectCode"));
        assertEquals("ai:businessProcess:start", action.get("permissionCode"));
        assertEquals("确认提交当前记录？", action.get("confirmText"));
        assertEquals("refreshList", action.get("successBehavior"));
    }

    @Test
    @DisplayName("structured visibleCondition compiles to a list display expression")
    void compileVisibleConditionToDisplayExpression() {
        BusinessProcessSchema schema = schema("START_MANUAL", Map.of(
                "positions", List.of("ROW"),
                "visibleCondition", Map.of(
                        "operator", "AND",
                        "rules", List.of(Map.of("field", "score", "operator", "EQ", "value", "1")))));

        List<Map<String, Object>> actions = compiler.compileSchema(schema, "CRM_APP", "submit_approval", "提交审批");

        assertEquals("score = 1", actions.get(0).get("displayCondition"));
    }

    @Test
    @DisplayName("event start nodes are not compiled into page actions")
    void ignoreEventStart() {
        BusinessProcessSchema schema = schema("START_EVENT", Map.of("eventType", "RECORD_CREATED"));
        assertTrue(compiler.compileSchema(schema, "CRM_APP", "auto", "自动").isEmpty());
    }

    private BusinessProcessSchema schema(String startType, Map<String, Object> config) {
        BusinessProcessSchema schema = new BusinessProcessSchema();
        schema.setSchemaVersion("1.0");
        schema.setProcessCode("submit_approval");
        BusinessProcessSchema.Subject subject = new BusinessProcessSchema.Subject();
        subject.setObjectId("20");
        subject.setObjectCode("order");
        schema.setSubject(subject);
        BusinessProcessNode start = new BusinessProcessNode();
        start.setId("start");
        start.setType(startType);
        start.setName("提交审批");
        start.setConfig(config);
        schema.setNodes(List.of(start));
        return schema;
    }
}
