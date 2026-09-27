package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

class DynamicCrudWritePolicyTest {

    private final DynamicCrudWritePolicy policy = new DynamicCrudWritePolicy(identifier -> {
    });

    @Test
    void fillsTenantLogicDeleteAndAuditFieldsWithoutOverridingExplicitValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "demo");
        data.put("create_by", 999L);
        Set<String> columns = Set.of("name", "tenant_id", "del_flag", "create_by", "create_dept",
            "create_time", "update_by", "update_time");

        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class);
             MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(7L);
            session.when(SessionHelper::getUserId).thenReturn(123L);
            session.when(SessionHelper::getMainOrgId).thenReturn(88L);

            policy.prepareInsert(data, columns);
        }

        assertEquals(7L, data.get("tenant_id"));
        assertEquals("0", data.get("del_flag"));
        assertEquals(999L, data.get("create_by"));
        assertEquals(88L, data.get("create_dept"));
        assertEquals(123L, data.get("update_by"));
        assertNotNull(data.get("create_time"));
        assertNotNull(data.get("update_time"));
    }

    @Test
    void stripsImmutableUpdateFieldsAndKeepsExplicitAuditActor() {
        Map<String, Object> data = new LinkedHashMap<>(Map.of(
            "id", 10L,
            "tenant_id", 7L,
            "name", "updated",
            "update_by", 999L
        ));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getUserId).thenReturn(123L);
            policy.prepareUpdate(data, Set.of("name", "update_by", "update_time"), "id");
        }

        assertFalse(data.containsKey("id"));
        assertFalse(data.containsKey("tenant_id"));
        assertEquals("updated", data.get("name"));
        assertEquals(999L, data.get("update_by"));
        assertNotNull(data.get("update_time"));
    }

    @Test
    void compilesLogicDeleteSetClauseFromAuditStrategy() {
        assertEquals("del_flag = :deletedValue, update_time = CURRENT_TIMESTAMP",
            policy.logicDeleteSetClause(Set.of("id", "del_flag", "update_time")));
        assertEquals("del_flag = :deletedValue",
            policy.logicDeleteSetClause(Set.of("id", "del_flag")));
    }
}
