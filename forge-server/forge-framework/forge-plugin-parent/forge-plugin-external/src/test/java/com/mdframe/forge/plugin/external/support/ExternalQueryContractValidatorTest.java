package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalQueryContractValidatorTest {

    private static final String INPUT_SCHEMA = """
            [
              {"name":"keyword","label":"查询条件","type":"string","required":true,"maxLength":8},
              {"name":"page","type":"integer"}
            ]
            """;
    private static final String OUTPUT_SCHEMA = """
            [{"name":"displayName","path":"member.name","label":"名称","type":"string"}]
            """;

    private final ExternalQueryContractValidator validator = new ExternalQueryContractValidator();

    @Test
    void shouldRequireReadOnlyMethodPermissionAndSchemasWhenEnabled() {
        assertThrows(BusinessException.class, () -> validator.validateConfiguration(
                true, "DELETE", true, "external:query", INPUT_SCHEMA, OUTPUT_SCHEMA));
        assertThrows(BusinessException.class, () -> validator.validateConfiguration(
                true, "POST", false, null, INPUT_SCHEMA, OUTPUT_SCHEMA));
        assertThrows(BusinessException.class, () -> validator.validateConfiguration(
                true, "POST", true, "external:query", "{}", OUTPUT_SCHEMA));
        assertThrows(BusinessException.class, () -> validator.validateConfiguration(
                true, "POST", true, "external:query", INPUT_SCHEMA, "[]"));
        assertThrows(BusinessException.class, () -> validator.validateConfiguration(
                true, "POST", true, "external:query",
                "[{\"name\":\"keyword\",\"type\":\"string\",\"maxLength\":\"invalid\"}]",
                OUTPUT_SCHEMA));
    }

    @Test
    void shouldRejectUnknownMissingWrongTypeAndOversizedParameters() {
        assertThrows(BusinessException.class,
                () -> validator.validateAndFilter(INPUT_SCHEMA, Map.of("unknown", "value")));
        assertThrows(BusinessException.class,
                () -> validator.validateAndFilter(INPUT_SCHEMA, Map.of("page", 1)));
        assertThrows(BusinessException.class,
                () -> validator.validateAndFilter(INPUT_SCHEMA, Map.of("keyword", "member", "page", 1.5)));
        assertThrows(BusinessException.class,
                () -> validator.validateAndFilter(INPUT_SCHEMA, Map.of("keyword", "123456789")));
    }

    @Test
    void shouldReturnOnlyDeclaredParametersInSchemaOrder() {
        Map<String, Object> result = validator.validateAndFilter(
                INPUT_SCHEMA, Map.of("page", 2L, "keyword", "member"));

        assertEquals(Map.of("keyword", "member", "page", 2L), result);
        assertEquals(java.util.List.of("keyword", "page"), result.keySet().stream().toList());
    }

    @Test
    void shouldConvertScalarStringParametersAndRejectComplexValues() {
        Map<String, Object> result = validator.validateAndFilter(
                "[{\"name\":\"userid\",\"label\":\"企微userid\",\"type\":\"string\",\"maxLength\":128}]",
                Map.of("userid", 1910000000000001111L));

        assertEquals(Map.of("userid", "1910000000000001111"), result);
        assertThrows(BusinessException.class, () -> validator.validateAndFilter(
                "[{\"name\":\"userid\",\"label\":\"企微userid\",\"type\":\"string\",\"maxLength\":128}]",
                Map.of("userid", Map.of("value", "yaoming"))));
    }
}
