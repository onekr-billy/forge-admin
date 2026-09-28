package com.mdframe.forge.starter.flow.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNotifyOutboxReplayMapperContractTest {

    @Test
    void deadLetterListingAndReplayMustBeTenantScopedAndStateGuarded() throws IOException {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/FlowNotifyOutboxMapper.xml"));

        String page = statement(xml, "select", "selectDeadLetterPage");
        assertThat(page)
                .contains("tenant_id = #{tenantId}", "delivery_status = 4")
                .doesNotContain("payload =");

        String replay = statement(xml, "update", "requeueDeadLetter");
        assertThat(replay)
                .contains("tenant_id = #{tenantId}", "id = #{id}", "delivery_status = 4",
                        "retry_count = 0", "replay_count = replay_count + 1",
                        "replayed_by = #{replayedBy}", "replay_reason = #{replayReason}")
                .doesNotContain("DELETE FROM");
    }

    private String statement(String xml, String tag, String id) {
        String marker = "<" + tag + " id=\"" + id + "\"";
        int start = xml.indexOf(marker);
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
