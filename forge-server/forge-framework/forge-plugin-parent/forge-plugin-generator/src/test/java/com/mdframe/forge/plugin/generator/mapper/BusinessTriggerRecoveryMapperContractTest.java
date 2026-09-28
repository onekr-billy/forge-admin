package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务触发器恢复 Mapper 契约")
class BusinessTriggerRecoveryMapperContractTest {

    @Test
    @DisplayName("恢复扫描、租约认领和完成更新均使用显式状态与租户 CAS")
    void recoverySqlUsesTenantLeaseAndRetryBoundaries() throws Exception {
        String xml = resource("mapper/BusinessTriggerLogMapper.xml");

        assertTrue(xml.contains("<select id=\"selectRecoveryCandidates\""));
        assertTrue(xml.contains("retry_count &lt; #{maxRetryCount}"));
        assertTrue(xml.contains("lock_time &lt;= #{staleBefore}"));
        assertTrue(xml.contains("event_source != 'LEGACY'"));
        assertTrue(xml.contains("<update id=\"claimExecution\""));
        assertTrue(xml.contains("WHERE tenant_id = #{tenantId}"));
        assertTrue(xml.contains("lock_owner = #{lockOwner}"));
        assertTrue(xml.contains("AND execute_status = 'PENDING'"));
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
