package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowBusinessWithdrawMapperContractTest {

    @Test
    void withdrawalResultMustBeTenantScopedAndPersistStableIdentity() throws Exception {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/FlowBusinessMapper.xml"));
        int start = mapper.indexOf("<update id=\"markWithdrawn\">");
        int end = mapper.indexOf("</update>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(mapper.substring(start, end))
                .contains("tenant_id = #{tenantId}",
                        "process_instance_id = #{processInstanceId}",
                        "action_idempotency_key = #{idempotencyKey}",
                        "action_request_digest = #{requestDigest}",
                        "action_type = #{actionType}");
    }

    @Test
    void migrationMustBeRepeatableAndCreateLookupIndex() throws Exception {
        String migration = Files.readString(Path.of(
                "../../../db/migration/V1.0.199__add_flow_business_action_idempotency.sql"));

        assertThat(migration)
                .contains("information_schema.COLUMNS", "information_schema.STATISTICS",
                        "action_idempotency_key", "action_request_digest", "action_type",
                        "idx_flow_business_action_idempotency")
                .doesNotContain("tenant_id = 0");
    }
}
