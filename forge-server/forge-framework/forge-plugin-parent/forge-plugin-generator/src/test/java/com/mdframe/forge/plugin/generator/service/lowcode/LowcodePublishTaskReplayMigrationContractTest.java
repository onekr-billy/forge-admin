package com.mdframe.forge.plugin.generator.service.lowcode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodePublishTaskReplayMigrationContractTest {

    private static final String MIGRATION =
            "V1.0.203__add_lowcode_publish_task_replay_audit.sql";

    @Test
    void migrationAddsIdempotentAuditColumnsAndUnassignedAdminPermission() throws Exception {
        String sql = Files.readString(resolveMigration(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("replay_count"));
        assertTrue(sql.contains("replayed_by"));
        assertTrue(sql.contains("replayed_time"));
        assertTrue(sql.contains("replay_reason"));
        assertTrue(sql.contains("ai:lowcode:publish-task:replay"));
        assertTrue(sql.contains("/ai/lowcode/app/publish-tasks/*/replay"));
        assertTrue(sql.contains("'pc', 1"));
        assertFalse(sql.contains("INSERT INTO sys_role_resource"));
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
