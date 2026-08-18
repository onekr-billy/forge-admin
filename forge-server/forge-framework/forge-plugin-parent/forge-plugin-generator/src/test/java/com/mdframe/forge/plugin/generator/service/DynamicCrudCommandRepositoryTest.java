package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeJdbcTemplateProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCrudCommandRepositoryTest {

    @Test
    void adjustsMultipleNumbersInOneStatementWithExpectedAndBoundConditions() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DynamicCrudRepository repository = spy(new DynamicCrudRepository(
                jdbcTemplate,
                mock(RuntimeJdbcTemplateProvider.class),
                mock(RuntimeDatabaseDialectFactory.class)));
        doReturn(true).when(repository).tableExists("order_item");
        doReturn(Set.of("picked_quantity", "pending_quantity", "status", "tenant_id"))
                .when(repository).getTableColumns("order_item");
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);

        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        deltas.put("picked_quantity", new BigDecimal("3"));
        deltas.put("pending_quantity", new BigDecimal("-3"));
        Map<String, BigDecimal> minimums = Map.of("pending_quantity", BigDecimal.ZERO);
        DynamicCrudRepository.SqlCondition expected = new DynamicCrudRepository.SqlCondition(
                "status = :commandExpected0", Map.of("commandExpected0", "ACTIVE"));

        try (MockedStatic<TenantContextHolder> tenantContext = mockStatic(TenantContextHolder.class);
             MockedStatic<SessionHelper> sessionHelper = mockStatic(SessionHelper.class)) {
            tenantContext.when(TenantContextHolder::getTenantId).thenReturn(7L);
            sessionHelper.when(SessionHelper::getUserId).thenReturn(123L);
            int affected = repository.adjustNumbersById(
                    "order_item", "id", 101L, deltas, minimums, Map.of(), expected);
            assertEquals(1, affected);
        }

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("UPDATE order_item SET picked_quantity = picked_quantity + :adjustDelta0, "
                + "pending_quantity = pending_quantity + :adjustDelta1 WHERE id = :id"));
        assertTrue(sql.contains("tenant_id = :tenantId"));
        assertTrue(sql.contains("(status = :commandExpected0)"));
        assertTrue(sql.contains("pending_quantity + :adjustDelta1 >= :adjustMin1"));
        assertEquals(new BigDecimal("3"), paramsCaptor.getValue().getValue("adjustDelta0"));
        assertEquals(new BigDecimal("-3"), paramsCaptor.getValue().getValue("adjustDelta1"));
        assertEquals("ACTIVE", paramsCaptor.getValue().getValue("commandExpected0"));
    }
}
