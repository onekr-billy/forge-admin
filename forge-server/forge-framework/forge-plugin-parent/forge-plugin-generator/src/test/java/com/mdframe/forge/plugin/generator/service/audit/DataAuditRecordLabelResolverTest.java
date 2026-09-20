package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataAuditRecordLabelResolverTest {

    @Test
    void usesBusinessNumberAndConfiguredDisplayFieldFromStoredRow() {
        AiBusinessObject object = new AiBusinessObject();
        object.setDisplayField("requestName");
        AiCrudConfig config = new AiCrudConfig();
        config.setOptions("{\"documentNoField\":\"requestNo\"}");

        String label = DataAuditRecordLabelResolver.resolve(
                object,
                config,
                null,
                Map.of("request_no", "PR-2026-001", "request_name", "办公用品采购"),
                List.of(field("requestNo", "request_no", "申请编号"),
                        field("requestName", "request_name", "申请名称")),
                "1001",
                new ObjectMapper());

        assertEquals("PR-2026-001 · 办公用品采购", label);
    }

    @Test
    void usesBeforeSnapshotForDeletedRecordAndIdAsLastFallback() {
        LowcodeFieldSchema title = field("title", "title", "标题");
        assertEquals("已删除申请", DataAuditRecordLabelResolver.resolve(
                new AiBusinessObject(), new AiCrudConfig(), Map.of("title", "已删除申请"), null,
                List.of(title), "1002", new ObjectMapper()));
        assertEquals("1003", DataAuditRecordLabelResolver.resolve(
                new AiBusinessObject(), new AiCrudConfig(), Map.of("id", "1003"), null,
                List.of(), "1003", new ObjectMapper()));
    }

    @Test
    void sensitiveDisplayFieldDoesNotEnterRecordLabel() {
        AiBusinessObject object = new AiBusinessObject();
        object.setDisplayField("customerName");
        LowcodeFieldSchema sensitiveName = field("customerName", "customer_name", "客户姓名");
        sensitiveName.setSensitiveType("NAME");

        assertEquals("1004", DataAuditRecordLabelResolver.resolve(
                object, new AiCrudConfig(), null, Map.of("customer_name", "张三"),
                List.of(sensitiveName), "1004", new ObjectMapper()));
    }

    private LowcodeFieldSchema field(String field, String column, String label) {
        LowcodeFieldSchema schema = new LowcodeFieldSchema();
        schema.setField(field);
        schema.setColumnName(column);
        schema.setLabel(label);
        return schema;
    }
}
