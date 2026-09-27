package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowRemoteCommandMapperContractTest {

    @Test
    void recoverySqlIsTenantScopedAndSupportsLeaseTakeover() throws IOException {
        String xml;
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/BusinessFlowRemoteCommandMapper.xml")) {
            if (stream == null) {
                throw new IOException("BusinessFlowRemoteCommandMapper.xml not found");
            }
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("WHERE tenant_id = #{tenantId}"));
        assertTrue(xml.contains("command_status = 'PROCESSING'"));
        assertTrue(xml.contains("lock_time &lt;= #{staleBefore}"));
        assertTrue(xml.contains("command_status = 'REMOTE_SUCCEEDED'"));
        assertTrue(xml.contains("WHERE command_status = 'REMOTE_SUCCEEDED'\n"
                + "           OR (\n"
                + "            retry_count &lt; #{maxRetryCount}"));
        assertTrue(xml.contains("process_instance_id IS NOT NULL"));
        assertFalse(xml.contains("${"));
    }
}
