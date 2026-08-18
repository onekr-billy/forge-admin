package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalRateLimitManagerTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldRejectWhenRateLimitExceededOrInfrastructureFails() {
        ExternalApi api = new ExternalApi();
        api.setId(20L);
        api.setRateLimitEnabled(true);
        api.setRateLimitQps(10);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter limiter = mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(limiter);
        when(limiter.tryAcquire()).thenReturn(false);

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertThrows(BusinessException.class, () -> new ExternalRateLimitManager(redissonClient).acquire(api));
            when(redissonClient.getRateLimiter(anyString())).thenThrow(new IllegalStateException("redis down"));
            assertThrows(BusinessException.class, () -> new ExternalRateLimitManager(redissonClient).acquire(api));
        }
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setUserType(2);
        user.setPermissions(Set.of());
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
