package com.mdframe.forge.plugin.generator.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCrudRecordQueryExecutorTest {

    @Test
    void compilesCompositeExistenceProbeWithNullAndExcludedId() {
        DynamicCrudRepository support = mock(DynamicCrudRepository.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        MapSqlParameterSource params = new MapSqlParameterSource();
        when(support.buildBaseQueryParams()).thenReturn(params);
        when(support.jdbc()).thenReturn(jdbc);
        doAnswer(invocation -> {
            StringBuilder where = invocation.getArgument(0);
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            return where.append(invocation.<String>getArgument(1));
        }).when(support).appendWhereCondition(any(StringBuilder.class), anyString());
        when(support.buildSelectSql(eq("SELECT COUNT(1)"), eq("biz_order"), any(StringBuilder.class)))
            .thenAnswer(invocation -> "SELECT COUNT(1) FROM biz_order WHERE " + invocation.getArgument(2));
        when(jdbc.queryForObject(anyString(), eq(params), eq(Long.class))).thenReturn(1L);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("order_code", "SO-001");
        values.put("deleted_at", null);

        boolean exists = new DynamicCrudRecordQueryExecutor(support).existsByColumns(
            "biz_order", values, "id", 99L, null);

        assertTrue(exists);
        assertEquals("SO-001", params.getValue("uniqueValue0"));
        assertFalse(params.hasValue("uniqueValue1"));
        assertEquals(99L, params.getValue("excludeId"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(params), eq(Long.class));
        assertTrue(sql.getValue().contains("order_code = :uniqueValue0"));
        assertTrue(sql.getValue().contains("deleted_at IS NULL"));
        assertTrue(sql.getValue().contains("id <> :excludeId"));
    }

    @Test
    void emptyBatchIdQueryReturnsWithoutJdbcAccess() {
        DynamicCrudRepository support = mock(DynamicCrudRepository.class);

        List<Map<String, Object>> rows = new DynamicCrudRecordQueryExecutor(support)
            .selectByIds("biz_order", "id", List.of(), null);

        assertEquals(List.of(), rows);
        verify(support).validateTableName("biz_order");
        verify(support).validateIdentifier("id");
    }
}
