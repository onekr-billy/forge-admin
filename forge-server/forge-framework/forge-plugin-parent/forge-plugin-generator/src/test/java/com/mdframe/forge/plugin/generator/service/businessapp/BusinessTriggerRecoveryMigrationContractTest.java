package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务触发器恢复迁移契约")
class BusinessTriggerRecoveryMigrationContractTest {

    private static final String MIGRATION = "V1.0.193__add_business_trigger_recovery_lease.sql";

    @Test
    @DisplayName("迁移补齐快照、租约、恢复索引和运行状态字典")
    void addsSnapshotLeaseAndStatusDictionary() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("COLUMN_NAME = 'trigger_snapshot'"));
        assertTrue(sql.contains("COLUMN_NAME = 'execution_digest'"));
        assertTrue(sql.contains("COLUMN_NAME = 'next_retry_time'"));
        assertTrue(sql.contains("COLUMN_NAME = 'lock_owner'"));
        assertTrue(sql.contains("COLUMN_NAME = 'lock_time'"));
        assertTrue(sql.contains("`idx_ai_trigger_log_recovery`"));
        assertTrue(sql.contains("execute_status = 'TODO'"));
        assertTrue(sql.contains("'PENDING' dict_value"));
        assertTrue(sql.contains("'DEAD', 'ai_business_trigger_execute_status'"));
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertFalse(sql.contains("${"));
        assertFalse(sql.toLowerCase().contains("tenant_id = 0"));
    }

    private String migrationSql() throws IOException {
        Path migration = locateMigration();
        assertTrue(Files.isRegularFile(migration), "找不到业务触发器恢复 Flyway: " + migration);
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
