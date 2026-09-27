package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNotifyReplayMigrationContractTest {

    @Test
    void migrationMustBeIdempotentAndKeepAuditFields() throws IOException {
        String sql = Files.readString(Path.of(
                "../../../db/migration/V1.0.194__add_flow_notify_dead_letter_replay_audit.sql"));

        assertThat(sql)
                .contains("information_schema.COLUMNS", "replay_count", "replayed_by",
                        "replayed_time", "replay_reason", "DEFAULT 0")
                .doesNotContain("tenant_id = 0", "${");
    }
}
