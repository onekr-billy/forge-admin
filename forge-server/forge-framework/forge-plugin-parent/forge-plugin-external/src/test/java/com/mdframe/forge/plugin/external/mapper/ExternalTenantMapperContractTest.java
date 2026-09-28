package com.mdframe.forge.plugin.external.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalTenantMapperContractTest {

    @Test
    void detailRelationAndDeleteStatementsMustCarryExplicitTenantConditions() throws IOException {
        assertTenantStatements("ExternalApiMapper.xml",
                "selectApiById", "selectApisBySystemId", "selectApiByCode", "deleteApiById");
        assertTenantStatements("ExternalSystemMapper.xml",
                "selectSystemById", "deleteSystemById");
        assertTenantStatements("ExternalApiLogMapper.xml",
                "selectLogById", "deleteLogById");
    }

    private void assertTenantStatements(String mapperName, String... statementIds) throws IOException {
        String xml = Files.readString(resolveMapper(mapperName));
        for (String statementId : statementIds) {
            int statementStart = xml.indexOf("id=\"" + statementId + "\"");
            assertThat(statementStart).as(statementId).isGreaterThanOrEqualTo(0);
            int statementEnd = xml.indexOf("</", statementStart);
            assertThat(xml.substring(statementStart, statementEnd))
                    .as(statementId)
                    .contains("tenant_id = #{tenantId}");
        }
    }

    private Path resolveMapper(String mapperName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("src/main/resources/mapper").resolve(mapperName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "forge-server/forge-framework/forge-plugin-parent/forge-plugin-external/src/main/resources/mapper")
                    .resolve(mapperName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 Mapper: " + mapperName);
    }
}
