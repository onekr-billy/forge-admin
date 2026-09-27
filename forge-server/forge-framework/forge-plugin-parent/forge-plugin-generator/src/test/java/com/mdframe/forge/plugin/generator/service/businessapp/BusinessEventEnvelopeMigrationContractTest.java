package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务事件信封迁移契约")
class BusinessEventEnvelopeMigrationContractTest {

    private static final String MIGRATION = "V1.0.191__add_business_event_envelope.sql";

    @Test
    @DisplayName("新增可信信封列并以租户、触发器、事件ID唯一认领")
    void addsEnvelopeAndUniqueClaim() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("COLUMN_NAME = 'event_id'"));
        assertTrue(sql.contains("COLUMN_NAME = 'event_source'"));
        assertTrue(sql.contains("COLUMN_NAME = 'event_version'"));
        assertTrue(sql.contains("COLUMN_NAME = 'event_digest'"));
        assertTrue(sql.contains("CONCAT('LEGACY:', id)"));
        assertTrue(sql.contains("`uk_ai_business_trigger_log_event`"));
        assertTrue(sql.contains("(`tenant_id`, `trigger_id`, `event_id`)"));
        assertTrue(sql.contains("MODIFY COLUMN `event_id` varchar(128) NOT NULL"));
        assertFalse(sql.contains("${"));
        assertFalse(sql.toLowerCase().contains("tenant_id = 0"));
    }

    private String migrationSql() throws IOException {
        Path migration = locateMigration();
        assertTrue(Files.isRegularFile(migration), "找不到业务事件信封 Flyway: " + migration);
        return Files.readString(migration, StandardCharsets.UTF_8);
    }

    private Path locateMigration() {
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (reactorRoot != null) {
            Path candidate = Path.of(reactorRoot).resolve("db/migration").resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("db/migration").resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            candidate = current.resolve("forge-server/db/migration").resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of("db/migration").resolve(MIGRATION);
    }
}
