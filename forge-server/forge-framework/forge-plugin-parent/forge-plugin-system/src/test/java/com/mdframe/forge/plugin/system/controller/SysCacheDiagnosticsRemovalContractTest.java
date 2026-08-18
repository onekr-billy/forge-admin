package com.mdframe.forge.plugin.system.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysCacheDiagnosticsRemovalContractTest {

    private static final Path CONTROLLER_DIR = Path.of(
            "src/main/java/com/mdframe/forge/plugin/system/controller");
    private static final Path DTO_DIR = Path.of(
            "src/main/java/com/mdframe/forge/plugin/system/dto");
    private static final Path RETIREMENT_MIGRATION = Path.of(
            "../../../db/migration/V1.0.123__remove_redis_cache_diagnostics.sql");

    @Test
    void rawRedisDiagnosticHttpSurfaceMustRemainRemoved() {
        assertThat(CONTROLLER_DIR.resolve("SysCacheController.java")).doesNotExist();
        assertThat(DTO_DIR.resolve("CacheInfoDTO.java")).doesNotExist();
        assertThat(CONTROLLER_DIR.resolve("SysManagedCachePolicyController.java")).exists();
    }

    @Test
    void migrationMustRetireOnlyLegacyDiagnosticResources() throws IOException {
        String migration = Files.readString(RETIREMENT_MIGRATION);

        assertThat(migration)
                .contains("DELETE FROM sys_role_resource", "SET del_flag = id")
                .contains("/system/cache/page", "/system/cache/getInfo", "/system/cache/remove",
                        "/system/cache/removeBatch", "/system/cache/clear", "/system/cache/metrics")
                .doesNotContain("/system/cache/policy/");
    }
}
