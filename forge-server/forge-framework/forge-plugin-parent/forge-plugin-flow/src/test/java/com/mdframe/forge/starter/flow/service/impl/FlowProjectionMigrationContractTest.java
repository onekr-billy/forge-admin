package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowProjectionMigrationContractTest {

    @Test
    void migrationCreatesOutboxAndFencingColumnsWithIdempotentGuards() throws IOException {
        String sql = Files.readString(Path.of(
                "../../../db/migration/V1.0.195__add_flow_projection_outbox.sql"));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS sys_flow_projection_outbox",
                        "UNIQUE KEY uk_flow_projection_event (tenant_id, event_id)",
                        "information_schema.COLUMNS", "projection_event_id", "projection_sequence")
                .doesNotContain("tenant_id = 0", "${");
    }
}
