package com.mdframe.forge.plugin.capability.identity.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityIdentityStartupMapperSqlContractTest {

    @Test
    void shouldAuditPlaceholderClientServiceBindingAndUnsafeGrant() throws IOException {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/CapabilityIdentityStartupMapper.xml"));

        assertThat(xml)
                .contains("LOWER(c.client_code) IN")
                .contains("FROM sys_user u")
                .contains("FROM sys_org o")
                .contains("FROM sys_user_org uo")
                .contains("FROM sys_user_org_role uor")
                .contains("c.actor_mode IN ('SERVICE', 'HYBRID')")
                .contains("FROM ai_capability_grant g")
                .contains("WHEN g.version_strategy = 'FOLLOW_MAJOR'")
                .contains("v.status &lt;&gt; 'PUBLISHED'")
                .contains("JSON_LENGTH(g.field_policy) = 0");
    }
}
