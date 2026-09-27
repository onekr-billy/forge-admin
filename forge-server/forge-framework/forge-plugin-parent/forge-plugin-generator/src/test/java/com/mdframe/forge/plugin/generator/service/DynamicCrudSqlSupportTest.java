package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.service.lowcode.runtime.MySqlRuntimeDatabaseDialect;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DynamicCrudSqlSupportTest {

    private final DynamicCrudRepository repository = mock(DynamicCrudRepository.class);
    private final DynamicCrudSqlSupport support = new DynamicCrudSqlSupport(repository,
        new RuntimeDatabaseDialectFactory(List.of(new MySqlRuntimeDatabaseDialect())));

    @Test
    void compilesDeterministicInsertAndUpdateCommands() {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("order_code", "SO-001");
        data.put("status", "DRAFT");

        assertEquals("INSERT INTO biz_order (order_code, status)"
            + " VALUES (:order_code, :status)", support.buildInsertSql("biz_order", data));
        assertEquals("UPDATE biz_order SET order_code = :order_code, status = :status WHERE id = :id",
            support.buildUpdateSql("biz_order", data, "id"));
        assertEquals("DELETE FROM biz_order WHERE id IN (:ids)",
            support.buildDeleteSql("biz_order", false, "id", true));
    }

    @Test
    void appendsParameterizedScopeConditionToBuilderAndSql() {
        DynamicCrudRepository.SqlCondition condition = new DynamicCrudRepository.SqlCondition(
            "status = :scopeStatus", Map.of("scopeStatus", "ACTIVE"));
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("id = :id");

        support.appendSqlCondition(where, params, condition);

        assertEquals("id = :id AND (status = :scopeStatus)", where.toString());
        assertEquals("ACTIVE", params.getValue("scopeStatus"));
        assertEquals("UPDATE biz_order SET name = :name WHERE id = :id AND (status = :scopeStatus)",
            support.appendSqlCondition(
                "UPDATE biz_order SET name = :name WHERE id = :id",
                new MapSqlParameterSource(), condition));
    }

    @Test
    void delegatesPaginationToRuntimeDialect() {
        assertEquals("SELECT * FROM biz_order LIMIT 20 OFFSET 40",
            support.paginate("SELECT * FROM biz_order", 3, 20));
        assertEquals("SELECT * FROM biz_order LIMIT 1 OFFSET 0",
            support.limit("SELECT * FROM biz_order", 0));
    }
}
