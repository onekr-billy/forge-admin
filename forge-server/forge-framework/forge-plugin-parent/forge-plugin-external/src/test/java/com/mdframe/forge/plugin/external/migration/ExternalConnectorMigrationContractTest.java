package com.mdframe.forge.plugin.external.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalConnectorMigrationContractTest {

    private static final String MIGRATION = "V1.0.101__add_external_connector_outbound_scene.sql";
    private static final String PERMISSION_MIGRATION = "V1.0.185__secure_external_api_permissions.sql";

    @Test
    void shouldAddIdempotentTenantOneOutboundSceneWithoutSensitiveDefaults() throws IOException {
        String sql = Files.readString(resolveMigration(MIGRATION));

        assertTrue(sql.contains("'sys_outbound_scene'"));
        assertTrue(sql.contains("'EXTERNAL_CONNECTOR'"));
        assertTrue(sql.contains("SELECT 1, 4, '外部连接器'"));
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("data.tenant_id = 1"));
        assertFalse(sql.contains("tenant_id = 0"));
        assertFalse(sql.contains("INSERT INTO sys_outbound_whitelist"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void shouldKeepEveryControllerPermissionTenantScopedAndHighRiskPermissionsPlatformOnly() throws IOException {
        String sql = Files.readString(resolveMigration(PERMISSION_MIGRATION));
        List<String> ordinaryPermissions = List.of(
                "external:system:query", "external:system:add", "external:system:edit",
                "external:system:remove", "external:api:query", "external:api:add", "external:api:edit",
                "external:api:remove", "external:proxy:invoke", "external:log:query", "external:log:remove");
        ordinaryPermissions.forEach(permission -> assertPermissionResource(sql, permission, 2));
        assertPermissionResource(sql, "external:proxy:debug", 1);
        assertPermissionResource(sql, "external:log:clear", 1);

        int roleGrantStart = sql.indexOf("-- 兼容已有“服务接口代理”菜单角色");
        int roleGrantEnd = sql.indexOf("-- 新建外部 API 默认必须开启调用权限校验", roleGrantStart);
        assertTrue(roleGrantStart >= 0 && roleGrantEnd > roleGrantStart);
        String roleGrant = sql.substring(roleGrantStart, roleGrantEnd);
        assertFalse(roleGrant.contains("external:proxy:debug"));
        assertFalse(roleGrant.contains("external:log:clear"));
    }

    private void assertPermissionResource(String sql, String permission, int minimumUserType) {
        int permissionIndex = sql.indexOf("'" + permission + "'");
        assertTrue(permissionIndex >= 0, permission);
        int statementStart = sql.lastIndexOf("INSERT INTO sys_resource", permissionIndex);
        int statementEnd = sql.indexOf(';', permissionIndex);
        assertTrue(statementStart >= 0 && statementEnd > permissionIndex, permission);
        String statement = sql.substring(statementStart, statementEnd);
        assertTrue(statement.contains("SELECT 1,"), permission);
        assertTrue(statement.contains("'pc', " + minimumUserType), permission);
        assertTrue(statement.contains("NOT EXISTS"), permission);
        assertTrue(statement.contains("tenant_id = 1"), permission);
    }

    private Path resolveMigration(String migration) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("db/migration").resolve(migration);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve("forge-server/db/migration").resolve(migration);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到迁移脚本: " + migration);
    }
}
