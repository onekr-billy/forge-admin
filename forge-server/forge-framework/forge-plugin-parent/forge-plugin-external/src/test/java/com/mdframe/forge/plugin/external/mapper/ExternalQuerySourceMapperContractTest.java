package com.mdframe.forge.plugin.external.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalQuerySourceMapperContractTest {

    @Test
    void shouldSelectOnlyEnabledTenantScopedLowcodeSourcesWithoutSensitiveColumns() throws IOException {
        String xml = Files.readString(resolveMapper());

        assertTrue(xml.contains("selectLowcodeQuerySources"));
        assertTrue(xml.contains("selectLowcodeQuerySourceByKey"));
        assertTrue(xml.contains("a.tenant_id = #{tenantId}"));
        assertTrue(xml.contains("a.lowcode_query_enabled = 1"));
        assertTrue(xml.contains("a.api_status = 1"));
        assertTrue(xml.contains("s.system_status = 1"));
        String lowcodeQueries = xml.substring(xml.indexOf("<select id=\"selectLowcodeQuerySources\""));
        assertFalse(lowcodeQueries.contains("SELECT a.*"));
    }

    private Path resolveMapper() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "src/main/resources/mapper/ExternalApiMapper.xml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "forge-server/forge-framework/forge-plugin-parent/forge-plugin-external/src/main/resources/mapper/ExternalApiMapper.xml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 ExternalApiMapper.xml");
    }
}
