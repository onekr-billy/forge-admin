package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicCrudQueryConditionCompilerTest {

    private final DynamicCrudQueryConditionCompiler compiler = new DynamicCrudQueryConditionCompiler(
            identifier -> {
                if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
                    throw new BusinessException("非法标识符");
                }
            }, () -> "id", () -> "id");

    @Test
    void compilesMultiValueAndOrLikeSearchAsBoundParameters() {
        StringBuilder where = new StringBuilder("tenant_id = :tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", 7L);
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("status", "ACTIVE,CLOSED");
        search.put("__orLike", List.of(Map.of("field", "name", "value", "demo")));

        compiler.appendSearchConditions(where, params, search, Set.of("status", "name"),
                Map.of("status", "eq", "name", "like"), Map.of("status", "status", "name", "name"));

        assertEquals("tenant_id = :tenantId AND status IN (:param_status) AND (name LIKE :or_like_0_name)",
                where.toString());
        assertEquals(List.of("ACTIVE", "CLOSED"), params.getValue("param_status"));
        assertEquals("%demo%", params.getValue("or_like_0_name"));
    }

    @Test
    void compilesCustomConditionRelationAndBetweenRange() {
        CustomQueryConditionDTO status = condition("status", "eq", "ACTIVE", null, "AND");
        CustomQueryConditionDTO amount = condition("amount", "between", 10, 20, "OR");
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();

        compiler.appendCustomConditions(where, params, List.of(status, amount), Set.of("status", "amount"),
                Map.of("status", "status", "amount", "amount"));

        assertEquals("(status = :custom_0_status OR amount BETWEEN :custom_1_amount_start "
                + "AND :custom_1_amount_end)", where.toString());
        assertEquals("ACTIVE", params.getValue("custom_0_status"));
        assertEquals(10, params.getValue("custom_1_amount_start"));
        assertEquals(20, params.getValue("custom_1_amount_end"));
    }

    @Test
    void rejectsUnknownCustomOperatorAndFiltersProjectionFields() {
        CustomQueryConditionDTO unsafe = condition("status", "execute", "x", null, "AND");
        assertThrows(BusinessException.class, () -> compiler.appendCustomConditions(
                new StringBuilder(), new MapSqlParameterSource(), List.of(unsafe), Set.of("status"),
                Map.of("status", "status")));

        assertEquals("SELECT id, name", compiler.buildCustomSelectClause(
                List.of("name", "missing"), Set.of("id", "name"), Map.of("id", "id", "name", "name")));
    }

    private CustomQueryConditionDTO condition(String field, String operator, Object value, Object valueEnd,
                                              String relation) {
        CustomQueryConditionDTO condition = new CustomQueryConditionDTO();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        condition.setValueEnd(valueEnd);
        condition.setRelation(relation);
        return condition;
    }
}
