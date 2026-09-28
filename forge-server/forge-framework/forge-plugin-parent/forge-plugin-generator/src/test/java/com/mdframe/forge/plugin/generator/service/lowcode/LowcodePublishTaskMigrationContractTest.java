package com.mdframe.forge.plugin.generator.service.lowcode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodePublishTaskMigrationContractTest {

    private static final String MIGRATION = "V1.0.202__add_lowcode_publish_task.sql";

    @Test
    void migrationCreatesRecoverableTaskWithRequiredOperationalIdentity() throws Exception {
        String sql = Files.readString(resolveMigration(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `ai_lowcode_publish_task`"));
        assertTrue(sql.contains("`request_id`"));
        assertTrue(sql.contains("`schema_hash`"));
        assertTrue(sql.contains("`runtime_datasource_code`"));
        assertTrue(sql.contains("`version_id`"));
        assertTrue(sql.contains("`operator_id`"));
        assertTrue(sql.contains("`command_digest`"));
        assertTrue(sql.contains("`task_status`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_ai_lowcode_publish_task_request`"));
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
