package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicCrudMoneyValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DynamicCrudFieldValuePipeline pipeline = new DynamicCrudFieldValuePipeline(
            objectMapper, null, null, null, new LowcodeEncryptConfigParser(objectMapper), null);

    @Test
    void convertsMajorUnitInputToMinorUnitStorage() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "12.30");

        pipeline.applyMoneyStorageWrite(data, config());

        assertEquals(1230L, data.get("cash_amount"));
    }

    @Test
    void convertsMinorUnitStorageToMajorUnitDisplay() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cash_amount", 1230L);

        pipeline.applyMoneyDisplayProjection(List.of(row), config());

        assertEquals(new BigDecimal("12.30"), row.get("cash_amount"));
    }

    @Test
    void rejectsMoneyInputWithMoreFractionalDigitsThanConfigured() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "12.345");

        assertThrows(BusinessException.class, () -> pipeline.applyMoneyStorageWrite(data, config()));
    }

    @Test
    void rejectsMoneyInputBelowConfiguredMinimum() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "0.00");

        assertThrows(BusinessException.class, () -> pipeline.applyMoneyStorageWrite(data, config()));
    }

    @Test
    void leavesDecimalMoneyFieldsUntouchedForLegacyCompatibility() {
        AiCrudConfig config = new AiCrudConfig();
        config.setModelSchema("""
                {"fields":[{"field":"amount","columnName":"amount","dataType":"decimal","componentType":"money","precision":2}]}
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", "12.30");

        pipeline.applyMoneyStorageWrite(data, config);

        assertEquals("12.30", data.get("amount"));
    }

    private AiCrudConfig config() {
        AiCrudConfig config = new AiCrudConfig();
        config.setModelSchema("""
                {"fields":[{"field":"cashAmount","columnName":"cash_amount","dataType":"bigint","componentType":"money","businessFieldType":"MONEY","precision":2,"basicProps":{"min":0.01}}]}
                """);
        return config;
    }

}
