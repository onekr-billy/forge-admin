package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务事件可信信封")
class BusinessEventEnvelopeTest {

    @Test
    @DisplayName("显式上游键生成稳定事件ID且载荷摘要忽略Map插入顺序")
    void generatesStableIdFromExplicitSourceKey() {
        Map<String, Object> firstData = new LinkedHashMap<>();
        firstData.put("status", "DRAFT");
        firstData.put("amount", 100);
        Map<String, Object> secondData = new LinkedHashMap<>();
        secondData.put("amount", 100);
        secondData.put("status", "DRAFT");

        BusinessEvent first = stamp(firstData, "flow-instance-1:APPROVED");
        BusinessEvent second = stamp(secondData, "flow-instance-1:APPROVED");

        assertEquals(first.getEventId(), second.getEventId());
        assertEquals(first.getEventDigest(), second.getEventDigest());
        assertTrue(BusinessEventEnvelope.isTrusted(first));
        assertTrue(BusinessEventEnvelope.isTrusted(second));
    }

    @Test
    @DisplayName("普通事件即使载荷相同也保留不同的发生ID")
    void separatesRepeatedOccurrencesWithSamePayload() {
        BusinessEvent first = stamp(Map.of("status", "DRAFT"));
        BusinessEvent second = stamp(Map.of("status", "DRAFT"));

        assertNotEquals(first.getEventId(), second.getEventId());
    }

    @Test
    @DisplayName("载荷、租户或协议版本被改写后信封失效")
    void rejectsMutatedEnvelope() {
        BusinessEvent payloadChanged = stamp(new LinkedHashMap<>(Map.of("status", "DRAFT")));
        payloadChanged.getRecordData().put("status", "APPROVED");
        BusinessEvent tenantChanged = stamp(Map.of("status", "DRAFT"));
        tenantChanged.setTenantId(2L);
        BusinessEvent outdated = stamp(Map.of("status", "DRAFT"));
        outdated.setEventVersion(0);

        assertFalse(BusinessEventEnvelope.isTrusted(payloadChanged));
        assertFalse(BusinessEventEnvelope.isTrusted(tenantChanged));
        assertFalse(BusinessEventEnvelope.isTrusted(outdated));
    }

    @Test
    @DisplayName("事件来源与事件类型不匹配时拒绝消费")
    void rejectsSourceTypeMismatch() {
        BusinessEvent event = BusinessEventEnvelope.stamp(BusinessEvent.builder()
                .eventType(BusinessEvent.FLOW_APPROVED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(1L)
                .build(), BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD);

        assertFalse(BusinessEventEnvelope.isTrusted(event));
    }

    @Test
    @DisplayName("Outbox 分配聚合序号后刷新完整摘要且保持逻辑摘要")
    void assignsAggregateSequenceWithoutChangingLogicalIdentity() {
        BusinessEvent event = stamp(Map.of("status", "DRAFT"), "stable-update");
        String eventId = event.getEventId();
        String logicalDigest = BusinessEventEnvelope.logicalDigest(event);

        BusinessEventEnvelope.assignAggregateSequence(event, 7L);

        assertEquals(eventId, event.getEventId());
        assertEquals(7L, event.getAggregateSequence());
        assertEquals(logicalDigest, BusinessEventEnvelope.logicalDigest(event));
        assertTrue(BusinessEventEnvelope.isTrusted(event));
    }

    private BusinessEvent stamp(Map<String, Object> recordData) {
        return stamp(recordData, null);
    }

    private BusinessEvent stamp(Map<String, Object> recordData, String stableSourceKey) {
        return BusinessEventEnvelope.stamp(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(recordData)
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build(), BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD, stableSourceKey);
    }
}
