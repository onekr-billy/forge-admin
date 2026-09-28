package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowStatusReconciliationMigrationContractTest {

    private static final String MIGRATION =
            "V1.0.200__add_business_flow_status_reconciliation.sql";

    @Test
    void migrationIsIdempotentAndCreatesRecoveryIndex() throws Exception {
        String sql = Files.readString(resolveMigration(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("status_sync_status"));
        assertTrue(sql.contains("status_sync_lock_owner"));
        assertTrue(sql.contains("status_sync_error_type"));
        assertTrue(sql.contains("idx_ai_business_flow_status_sync"));
        assertTrue(sql.contains("DEFAULT ''PENDING''"));
        assertFalse(sql.contains("tenant_id = 0"));
        assertFalse(sql.contains("${"));
    }

    private Path resolveMigration() {
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (reactorRoot != null) {
            Path candidate = Path.of(reactorRoot).resolve("db/migration").resolve(MIGRATION);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("forge-server/db/migration").resolve(MIGRATION);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve("db/migration").resolve(MIGRATION);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of("db/migration").resolve(MIGRATION);
    }
}
