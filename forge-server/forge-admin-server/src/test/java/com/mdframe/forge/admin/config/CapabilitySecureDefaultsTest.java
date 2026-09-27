package com.mdframe.forge.admin.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilitySecureDefaultsTest {

    @Test
    void publicCapabilitySurfacesShouldBeDisabledWithoutEnvironmentOverrides() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("${FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED:false}")
                .contains("${FORGE_CAPABILITY_IDENTITY_ENABLED:${FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED:${FORGE_MCP_ENABLED:false}}}")
                .contains("${FORGE_CAPABILITY_FLOW_ACTIONS_ENABLED:${FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED:${FORGE_MCP_ENABLED:false}}}")
                .doesNotContain("${FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED:true}")
                .doesNotContain("${FORGE_MCP_ENABLED:true}");
    }
}
