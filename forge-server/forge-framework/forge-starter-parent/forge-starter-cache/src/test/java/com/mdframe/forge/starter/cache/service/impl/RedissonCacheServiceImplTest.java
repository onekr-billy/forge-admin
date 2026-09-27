package com.mdframe.forge.starter.cache.service.impl;

import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedissonCacheServiceImplTest {

    @Test
    void shouldIncrementAndSetInitialExpiryInOneLuaExecution() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.<Long>eval(
                eq(RScript.Mode.READ_WRITE),
                contains("redis.call('INCRBY'"),
                eq(RScript.ReturnType.INTEGER),
                eq(List.of("captcha:verify:failure:sms:test")),
                eq(1L),
                eq(600_000L)))
                .thenReturn(3L);

        RedissonCacheServiceImpl service = new RedissonCacheServiceImpl(redissonClient);

        assertThat(service.incrementWithExpiry(
                "captcha:verify:failure:sms:test", 1L, 10L, TimeUnit.MINUTES))
                .isEqualTo(3L);
    }

    @Test
    void shouldRejectCounterWithoutPositiveExpiry() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RedissonCacheServiceImpl service = new RedissonCacheServiceImpl(redissonClient);

        assertThatThrownBy(() -> service.incrementWithExpiry("counter", 1L, 0L, TimeUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redissonClient);
    }
}
