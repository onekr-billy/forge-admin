package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowCallbackInboxMapperContractTest {

    @Test
    void recoverySqlIsTenantScopedOrderedAndSingleConsumer() throws IOException {
        String xml;
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/BusinessFlowCallbackInboxMapper.xml")) {
            if (stream == null) {
                throw new IOException("BusinessFlowCallbackInboxMapper.xml not found");
            }
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("WHERE tenant_id = #{tenantId}"));
        assertTrue(xml.contains("earlier_event.event_sequence &lt; current_event.event_sequence"));
        assertTrue(xml.contains("active_event.consume_status = 'PROCESSING'"));
        assertTrue(xml.contains("SELECT MAX(event_sequence)"));
        assertTrue(xml.contains("lock_time &lt;= #{staleBefore}"));
        assertTrue(xml.contains("lock_owner = #{inbox.lockOwner}"));
        assertFalse(xml.contains("${"));
    }
}
