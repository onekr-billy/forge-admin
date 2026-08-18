package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class DataQueryRuntimeCacheTest {

    private final DataQueryRuntimeCache cache = new DataQueryRuntimeCache(mock(ICacheService.class));

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldHashParametersAndProduceStableKey() {
        DataDataset dataset = new DataDataset();
        dataset.setId(30L);
        dataset.setCacheEnabled(1);
        DataDatasetQueryDTO first = query(linkedParams("mobile", "13800138000", "orderNo", "P2026081001"));
        DataDatasetQueryDTO second = query(linkedParams("orderNo", "P2026081001", "mobile", "13800138000"));

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            String firstKey = cache.buildKey(dataset, first, List.of("memberName"), 1, 20);
            String secondKey = cache.buildKey(dataset, second, List.of("memberName"), 1, 20);
            assertEquals(firstKey, secondKey);
            assertFalse(firstKey.contains("13800138000"));
            assertFalse(firstKey.contains("P2026081001"));
        }
    }

    private DataDatasetQueryDTO query(Map<String, Object> params) {
        DataDatasetQueryDTO query = new DataDatasetQueryDTO();
        query.setParams(params);
        return query;
    }

    private Map<String, Object> linkedParams(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(firstKey, firstValue);
        params.put(secondKey, secondValue);
        return params;
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
