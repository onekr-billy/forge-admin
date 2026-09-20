package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.audit.DataAuditWriteContextDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("DataAuditPayloadSupport")
class DataAuditPayloadSupportTest {

    @Test
    @DisplayName("只保留原因和预期修订号，丢弃客户端伪造身份")
    void keepsOnlySafeClientFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "订单");
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reason", " 更正金额 ");
        audit.put("expectedRevision", 3);
        audit.put("actorId", "999");
        audit.put("tenantId", 2);
        audit.put("sourceType", "SYSTEM");
        audit.put("beforeData", Map.of("amount", 1));
        payload.put("_dataAudit", audit);

        DataAuditWriteContextDTO context = DataAuditPayloadSupport.extractAndStrip(payload);

        assertEquals(" 更正金额 ", context.getReason());
        assertEquals(3L, context.getExpectedRevision());
        assertFalse(payload.containsKey("_dataAudit"));
        assertEquals("订单", payload.get("name"));
        assertNull(payload.get("actorId"));
    }
}
