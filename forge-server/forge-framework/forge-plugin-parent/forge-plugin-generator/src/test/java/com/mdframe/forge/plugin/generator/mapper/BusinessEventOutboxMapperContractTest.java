package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventOutboxMapperContractTest {

    @Test
    void candidateAndClaimAreTenantScopedOrderedAndLeaseGuarded() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/BusinessEventOutboxMapper.xml"));
        String candidates = statement(xml, "select", "selectDeliveryCandidates");
        String claim = statement(xml, "update", "claimDelivery");

        assertThat(candidates).contains(
                "earlier_event.tenant_id = current_event.tenant_id",
                "earlier_event.aggregate_sequence &lt; current_event.aggregate_sequence",
                "earlier_event.delivery_status != 'DELIVERED'",
                "current_event.lock_time &lt;= #{staleBefore}");
        assertThat(claim).contains(
                "current_event.tenant_id = #{tenantId}",
                "earlier_event.id IS NULL",
                "current_event.retry_count &lt; #{maxRetryCount}",
                "current_event.lock_time &lt;= #{staleBefore}");
        assertThat(statement(xml, "update", "expireExhaustedLease"))
                .contains("tenant_id = #{tenantId}", "delivery_status = 'PROCESSING'",
                        "retry_count &gt;= #{maxRetryCount}", "LeaseExpiredAfterRetryLimit");
    }

    @Test
    void resultTransitionsAreFencedByTenantStatusAndLeaseOwner() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/BusinessEventOutboxMapper.xml"));
        assertThat(statement(xml, "update", "markDelivered"))
                .contains("tenant_id = #{outbox.tenantId}", "delivery_status = 'PROCESSING'",
                        "lock_owner = #{outbox.lockOwner}");
        assertThat(statement(xml, "update", "markFailed"))
                .contains("tenant_id = #{outbox.tenantId}", "delivery_status = 'PROCESSING'",
                        "lock_owner = #{outbox.lockOwner}");
    }

    private String statement(String xml, String tag, String id) {
        int start = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
