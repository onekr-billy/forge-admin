package com.mdframe.forge.flow.client.helper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowClientHelperAutoConfigurationTest {

    @Test
    void redisStreamIsDefaultAndLegacyPubSubRequiresExplicitOptIn() throws Exception {
        Method streamMethod = FlowClientHelperAutoConfiguration.class.getMethod(
                "flowRedisStreamSubscriber",
                org.springframework.data.redis.core.StringRedisTemplate.class,
                FlowEventSubscriber.class,
                String.class,
                String.class,
                String.class,
                int.class,
                long.class,
                long.class,
                long.class);
        ConditionalOnProperty streamCondition = streamMethod.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"redis-stream-subscribe"}, streamCondition.name());
        assertTrue(streamCondition.matchIfMissing());

        Method legacyMethod = FlowClientHelperAutoConfiguration.class.getMethod(
                "flowRedisListenerContainer",
                org.springframework.data.redis.connection.RedisConnectionFactory.class,
                FlowEventSubscriber.class);
        ConditionalOnProperty legacyCondition = legacyMethod.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"redis-subscribe"}, legacyCondition.name());
        assertFalse(legacyCondition.matchIfMissing());
    }
}
