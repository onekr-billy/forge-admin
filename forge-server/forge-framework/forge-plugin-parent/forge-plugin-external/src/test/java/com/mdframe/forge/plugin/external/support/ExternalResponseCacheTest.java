package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class ExternalResponseCacheTest {

    private final ExternalResponseCache cache = new ExternalResponseCache(mock(ICacheService.class));

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldBuildStableHashedKeyWithoutSensitiveValues() {
        ExternalApi api = new ExternalApi();
        api.setId(20L);
        api.setApiMethod("GET");
        api.setCacheEnabled(true);
        api.setCacheKeyTemplate("member:{mobile}");
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("mobile", "13800138000");
        first.put("token", "secret-token");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("token", "secret-token");
        second.put("mobile", "13800138000");

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            String firstKey = cache.buildKey(api, first);
            String secondKey = cache.buildKey(api, second);
            assertEquals(firstKey, secondKey);
            assertFalse(firstKey.contains("13800138000"));
            assertFalse(firstKey.contains("secret-token"));
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
