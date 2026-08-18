package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.starter.outbound.model.OutboundResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalRetryExecutorTest {

    private final ExternalRetryExecutor executor = new ExternalRetryExecutor();

    @Test
    void shouldRetryRetryableGetResponse() {
        ExternalSystem system = retrySystem();
        AtomicInteger calls = new AtomicInteger();

        OutboundResponse response = executor.execute(system, "GET", () ->
                new OutboundResponse(calls.incrementAndGet() == 1 ? 503 : 200, Map.of(), new byte[0]));

        assertEquals(200, response.getStatusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void shouldNotRetryPost() {
        ExternalSystem system = retrySystem();
        AtomicInteger calls = new AtomicInteger();

        OutboundResponse response = executor.execute(system, "POST", () -> {
            calls.incrementAndGet();
            return new OutboundResponse(503, Map.of(), new byte[0]);
        });

        assertEquals(503, response.getStatusCode());
        assertEquals(1, calls.get());
    }

    private ExternalSystem retrySystem() {
        ExternalSystem system = new ExternalSystem();
        system.setRetryEnabled(true);
        system.setRetryMaxAttempts(3);
        system.setRetryBackoffInterval(0);
        return system;
    }
}
