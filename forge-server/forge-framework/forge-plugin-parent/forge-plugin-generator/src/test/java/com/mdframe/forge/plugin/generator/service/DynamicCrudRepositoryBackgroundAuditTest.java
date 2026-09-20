package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeJdbcTemplateProvider;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamicCrudRepositoryBackgroundAuditTest {
    @Test
    void explicitExecutionIdentityStillFillsAuditUserWithoutWebRequest() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DynamicCrudRepository repository = repository(jdbc);
        com.mdframe.forge.starter.core.session.LoginUser user = new com.mdframe.forge.starter.core.session.LoginUser();
        user.setUserId(123L);
        user.setTenantId(7L);
        var identity = new com.mdframe.forge.starter.core.context.ExecutionIdentity(
                user, "USER", 123L, null, 900L, "test-client", "test-token-id", Set.of());
        try (var ignored = com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder.open(identity)) {
            TenantContextHolder.executeWithTenant(7L, () -> repository.updateById(
                    "demo_table", 101L, new LinkedHashMap<>(Map.of("flow_status", "CANCELED"))));
        }
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(any(String.class), params.capture());
        assertEquals(123L, params.getValue().getValue("update_by"));
        assertTrue(com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder.current().isEmpty());
    }

    @Test
    void backgroundInsertKeepsTenantAndTimestampsWithoutInventingActor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DynamicCrudRepository repository = repository(jdbc);
        doReturn(Set.of("id", "tenant_id", "flow_status", "create_by", "create_dept", "create_time", "update_by", "update_time"))
                .when(repository).getTableColumns("demo_table");
        TenantContextHolder.executeWithTenant(7L, () -> repository.insert(
                "demo_table", new LinkedHashMap<>(Map.of("id", 101L, "flow_status", "CANCELED"))));
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(any(String.class), params.capture());
        assertEquals(7L, params.getValue().getValue("tenant_id"));
        assertNotNull(params.getValue().getValue("create_time"));
        assertFalse(params.getValue().hasValue("create_by"));
        assertFalse(params.getValue().hasValue("create_dept"));
    }

    @Test
    void backgroundStatusUpdateDoesNotRequireWebSessionAndKeepsTenantPredicate() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DynamicCrudRepository repository = repository(jdbc);
        TenantContextHolder.executeWithTenant(7L, () -> repository.updateById(
                "demo_table", 101L, new LinkedHashMap<>(Map.of("flow_status", "CANCELED"))));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("tenant_id = :tenantId"));
        assertEquals(7L, params.getValue().getValue("tenantId"));
        assertEquals("CANCELED", params.getValue().getValue("flow_status"));
        assertNotNull(params.getValue().getValue("update_time"));
        assertFalse(params.getValue().hasValue("update_by"));
    }

    @Test
    void authenticatedUpdateStillFillsUserAndOtherErrorsAreNotSwallowed() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DynamicCrudRepository repository = repository(jdbc);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getUserId).thenReturn(123L);
            TenantContextHolder.executeWithTenant(7L, () -> repository.updateById(
                    "demo_table", 101L, new LinkedHashMap<>(Map.of("flow_status", "CANCELED"))));
            ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
            verify(jdbc).update(any(String.class), params.capture());
            assertEquals(123L, params.getValue().getValue("update_by"));
            session.when(SessionHelper::getUserId).thenThrow(new IllegalStateException("session store unavailable"));
            assertThrows(IllegalStateException.class, () -> TenantContextHolder.executeWithTenant(7L,
                    () -> repository.updateById("demo_table", 101L,
                            new LinkedHashMap<>(Map.of("flow_status", "CANCELED")))));
        }
    }

    private DynamicCrudRepository repository(NamedParameterJdbcTemplate jdbc) {
        DynamicCrudRepository repository = spy(new DynamicCrudRepository(jdbc,
                mock(RuntimeJdbcTemplateProvider.class), mock(RuntimeDatabaseDialectFactory.class)));
        doReturn(true).when(repository).tableExists("demo_table");
        doReturn(Set.of("id", "tenant_id", "flow_status", "update_by", "update_time"))
                .when(repository).getTableColumns("demo_table");
        when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        return repository;
    }
}
