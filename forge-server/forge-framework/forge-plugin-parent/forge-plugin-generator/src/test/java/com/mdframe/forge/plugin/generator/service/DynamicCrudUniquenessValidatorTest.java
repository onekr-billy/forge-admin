package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Dynamic CRUD uniqueness validator")
class DynamicCrudUniquenessValidatorTest {

    private DynamicCrudRepository repository;
    private DynamicCrudUniquenessValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(DynamicCrudRepository.class);
        validator = new DynamicCrudUniquenessValidator(repository, new ObjectMapper());
        when(repository.getColumnMapping("biz_record")).thenReturn(Map.of(
                "code", "code", "serialNo", "serial_no", "serial_no", "serial_no"));
        when(repository.getTableColumns("biz_record")).thenReturn(Set.of("id", "code", "serial_no"));
    }

    @Test
    @DisplayName("normalizes model-declared unique values before querying")
    void normalizesModelUniqueValueBeforeQuerying() {
        AiCrudConfig config = config("""
                {
                  "fields": [
                    {"field":"code","columnName":"code","label":"编码",
                     "advancedProps":{"unique":true}}
                  ]
                }
                """, "[]");
        when(repository.existsByColumns(
                eq("biz_record"), eq(Map.of("code", "A001")), eq("id"), isNull(), isNull()))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(
                        config, "biz_record", Map.of("code", " A001 "), null, null, "id"));

        assertEquals("编码已存在", exception.getMessage());
    }

    @Test
    @DisplayName("skips unchanged unique fields during partial update")
    void skipsUnchangedUniqueFieldDuringPartialUpdate() {
        AiCrudConfig config = config("""
                {
                  "fields": [
                    {"field":"code","columnName":"code","label":"编码",
                     "advancedProps":{"unique":true}}
                  ]
                }
                """, "[]");

        validator.validate(
                config,
                "biz_record",
                Map.of("name", "updated"),
                Map.of("id", 7L, "code", "A001"),
                7L,
                "id");

        verify(repository, never()).existsByColumns(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("honors unique declarations from edit schema")
    void honorsEditSchemaUniqueDeclaration() {
        AiCrudConfig config = config("{\"fields\":[]}", """
                [{"field":"serialNo","label":"流水号","unique":true}]
                """);
        when(repository.existsByColumns(
                eq("biz_record"), eq(Map.of("serial_no", "SN-01")), eq("id"), isNull(), isNull()))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(
                        config, "biz_record", Map.of("serialNo", "SN-01"), null, null, "id"));

        assertEquals("流水号已存在", exception.getMessage());
    }

    private AiCrudConfig config(String modelSchema, String editSchema) {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("biz_record_runtime");
        config.setTableName("biz_record");
        config.setModelSchema(modelSchema);
        config.setEditSchema(editSchema);
        return config;
    }
}
