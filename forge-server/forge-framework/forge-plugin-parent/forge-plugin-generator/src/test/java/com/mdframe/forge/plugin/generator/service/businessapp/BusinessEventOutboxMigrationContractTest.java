package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventOutboxMigrationContractTest {

    @Test
    void migrationCreatesIdempotentOutboxAndAggregateSequenceTables() throws IOException {
        String sql = Files.readString(Path.of(
                "../../../db/migration/V1.0.196__add_business_event_outbox.sql"));

        assertThat(sql).contains(
                        "CREATE TABLE IF NOT EXISTS `ai_business_event_sequence`",
                        "CREATE TABLE IF NOT EXISTS `ai_business_event_outbox`",
                        "UNIQUE KEY `uk_ai_business_event_outbox_event` (`tenant_id`, `event_id`)",
                        "UNIQUE KEY `uk_ai_business_event_outbox_sequence` (`tenant_id`, `aggregate_key`, `aggregate_sequence`)")
                .doesNotContain("tenant_id = 0", "${");
    }
}
