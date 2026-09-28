package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowStatusReconciliationMapperContractTest {

    @Test
    void reconciliationSqlUsesLeaseFencingAndExplicitTenantIdentity() throws IOException {
        String xml;
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/BusinessFlowInstanceLinkMapper.xml")) {
            if (stream == null) {
                throw new IOException("BusinessFlowInstanceLinkMapper.xml not found");
            }
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("status_sync_status = 'PROCESSING'"));
        assertTrue(xml.contains("status_sync_lock_time &lt;= #{staleBefore}"));
        assertTrue(xml.contains("status_sync_lock_owner = #{link.statusSyncLockOwner}"));
        assertTrue(xml.contains("WHERE tenant_id = #{tenantId}"));
        assertTrue(xml.contains("flow_status IN ('STARTED', 'RUNNING', 'IN_PROCESS', 'NEED_MODIFY')"));
        assertTrue(xml.contains("<update id=\"requeueDeadStatusSync\">"));
        assertTrue(xml.contains("status_sync_status = 'DEAD'"));
        assertTrue(xml.contains("status_sync_status = 'PENDING'"));
        assertTrue(xml.contains("status_sync_replay_count = status_sync_replay_count + 1"));
        assertTrue(xml.contains("status_sync_replayed_by = #{replayedBy}"));
        assertTrue(xml.contains("status_sync_replay_reason = #{replayReason}"));
        assertFalse(xml.contains("${"));
    }
}
