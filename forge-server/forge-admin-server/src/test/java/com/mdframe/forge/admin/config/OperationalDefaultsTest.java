package com.mdframe.forge.admin.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalDefaultsTest {

    @Test
    void apiPermissionResourcesShouldBeOptInAndBackgroundSqlShouldNotLogAtDebugByDefault()
            throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String logbackXml = Files.readString(Path.of("src/main/resources/logback.xml"));

        assertThat(applicationYaml)
                .contains("${FORGE_AUTH_API_PERMISSION_COVERAGE_ENABLED:false}")
                .contains("${FORGE_BUSINESS_EVENT_OUTBOX_SCAN_INTERVAL_MS:5000}")
                .contains("com.mdframe.forge: ${FORGE_LOG_LEVEL:info}");
        assertThat(logbackXml)
                .contains("${FORGE_LOG_LEVEL:-INFO}")
                .contains("${FORGE_SQL_LOG_LEVEL:-INFO}")
                .doesNotContain("<logger name=\"com.mdframe.forge\" level=\"debug\"");
    }
}
