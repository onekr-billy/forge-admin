package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("business flow start context assembler")
class BusinessFlowStartContextAssemblerTest {

    private final BusinessFlowStartContextAssembler assembler =
            BusinessFlowStartContextAssembler.standard();

    @Test
    @DisplayName("assembles scalar aliases mapped variables and title")
    void assemblesStartContext() {
        JSONObject binding = JSON.parseObject("""
                {
                  "titleTemplate": "${orderNo}-${applicantName}",
                  "variableMapping": [
                    {"formField": "applicant_name", "flowVariable": "applicant"}
                  ]
                }
                """);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("order_no", "PO-1001");
        record.put("lines", List.of(Map.of("sku", "A")));
        record.put("main", Map.of("applicantName", "Alice"));

        BusinessFlowStartContextAssembler.StartContext context = assembler.assemble(
                binding, record, Map.of("urgent", true), "purchase_order");

        assertEquals("PO-1001", context.variables().get("order_no"));
        assertEquals("PO-1001", context.variables().get("orderNo"));
        assertEquals("Alice", context.variables().get("applicant"));
        assertEquals(true, context.variables().get("urgent"));
        assertFalse(context.variables().containsKey("lines"));
        assertFalse(context.variables().containsKey("main"));
        assertEquals("PO-1001-Alice", context.title());
    }

    @Test
    @DisplayName("record accessor supports aliases main wrappers and explicit null")
    void readsCompatibleRecordShapes() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("nullable_field", null);
        record.put("main", Map.of("documentNo", "DOC-9"));

        assertTrue(BusinessFlowRecordValues.contains(record, "nullableField"));
        assertNull(BusinessFlowRecordValues.read(record, "nullableField"));
        assertEquals("DOC-9", BusinessFlowRecordValues.read(record, "document_no"));
        assertTrue(BusinessFlowRecordValues.sameField("document_no", "documentNo"));
    }

    @Test
    @DisplayName("server-owned variables are rejected before target mutation")
    void rejectsServerOwnedVariables() {
        Map<String, Object> target = new LinkedHashMap<>(Map.of("existing", 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assembler.mergeRequestedVariables(target, Map.of(
                        "objectCode", "forged",
                        "businessKey", "forged:1")));

        assertEquals("启动变量不能覆盖服务端业务上下文：businessKey, objectCode",
                exception.getMessage());
        assertEquals(Map.of("existing", 1), target);
    }

    @Test
    @DisplayName("selected approvers and custom variables pass through")
    void acceptsCallerOwnedVariables() {
        Map<String, Object> target = new LinkedHashMap<>();
        Map<String, String> selectedApprovers = Map.of("managerApprove", "101,102");

        assembler.mergeRequestedVariables(target, Map.of(
                "PROCESS_START_USER", selectedApprovers,
                "urgent", true));

        assertEquals(selectedApprovers, target.get("PROCESS_START_USER"));
        assertEquals(true, target.get("urgent"));
    }

    @Test
    @DisplayName("renders extended title context and preserves fallback")
    void rendersTitleContext() {
        assertEquals("采购单-Alice-PO-7", assembler.renderTitle(
                "${objectName}-${initiatorName}-${order_no}",
                Map.of("orderNo", "PO-7"),
                "purchase_order", "Alice", "采购单"));
        assertEquals("purchase_order 审批申请", assembler.renderTitle(
                " ", Map.of(), "purchase_order", null, null));
    }
}
