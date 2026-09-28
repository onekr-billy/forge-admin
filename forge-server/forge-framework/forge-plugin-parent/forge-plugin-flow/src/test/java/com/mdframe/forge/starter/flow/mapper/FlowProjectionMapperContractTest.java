package com.mdframe.forge.starter.flow.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowProjectionMapperContractTest {

    @Test
    void outboxClaimMustBeTenantScopedOrderedAndLeaseGuarded() throws IOException {
        String xml = read("FlowProjectionOutboxMapper.xml");
        String claim = statement(xml, "update", "claim");
        assertThat(claim)
                .contains("current_event.tenant_id = #{tenantId}",
                        "previous_event.id &lt; current_event.id",
                        "previous_event.projection_status != 2",
                        "current_event.retry_count &lt; #{maxRetryCount}",
                        "current_event.lock_time &lt;= #{staleBefore}");
    }

    @Test
    void mirrorWritesMustUseTenantAndMonotonicProjectionSequence() throws IOException {
        assertThat(statement(read("FlowTaskMapper.xml"), "update", "applyProjection"))
                .contains("tenant_id = #{tenantId}", "task_id = #{taskId}",
                        "COALESCE(projection_sequence, 0) &lt; #{eventSequence}");
        assertThat(statement(read("FlowBusinessMapper.xml"), "update", "applyProjection"))
                .contains("tenant_id = #{tenantId}", "process_instance_id = #{processInstanceId}",
                        "COALESCE(projection_sequence, 0) &lt; #{eventSequence}");
        assertThat(statement(read("FlowFormInstanceMapper.xml"), "update", "applyProjectionStatus"))
                .contains("tenant_id = #{tenantId}", "deleted = 0",
                        "COALESCE(projection_sequence, 0) &lt; #{eventSequence}");
        assertThat(statement(read("FlowTaskCandidateMapper.xml"), "insert", "upsertProjection"))
                .contains("VALUES(projection_sequence) &gt; COALESCE(projection_sequence, 0)",
                        "projection_sequence = GREATEST");
    }

    private String read(String name) throws IOException {
        return Files.readString(Path.of("src/main/resources/mapper", name));
    }

    private String statement(String xml, String tag, String id) {
        int start = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
