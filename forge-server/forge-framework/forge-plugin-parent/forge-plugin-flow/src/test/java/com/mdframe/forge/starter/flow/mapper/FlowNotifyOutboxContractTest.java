package com.mdframe.forge.starter.flow.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNotifyOutboxContractTest {

    @Test
    void mapperUsesTenantScopedCasAndStaleLeaseRecovery() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/FlowNotifyOutboxMapper.xml"));
        assertThat(xml).contains(
                "tenant_id = #{tenantId}",
                "retry_count &lt; #{maxRetryCount}",
                "delivery_status IN (0, 3)",
                "delivery_status = 1 AND lock_time &lt;= #{staleBefore}",
                "AND NOT EXISTS (",
                "previous_event.id &lt; current_event.id",
                "previous_event.delivery_status != 2",
                "previous_event.id IS NULL",
                "AND lock_owner = #{lockOwner}");
    }

    @Test
    void migrationDefinesUniqueEventAndDispatchOrdering() throws IOException {
        String migration = Files.readString(Path.of("../../../db/migration/V1.0.192__add_flow_notify_outbox.sql"));
        assertThat(migration).contains(
                "UNIQUE KEY uk_flow_notify_outbox_event (tenant_id, event_id)",
                "id bigint NOT NULL AUTO_INCREMENT",
                "payload_hash char(64) NOT NULL",
                "occurred_at datetime(3) NOT NULL",
                "delivery_status tinyint NOT NULL DEFAULT 0",
                "idx_flow_notify_outbox_dispatch");
    }
}
