package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicCrudMoneyValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsMajorUnitInputToMinorUnitStorage() throws Exception {
        DynamicCrudService service = service();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "12.30");

        invoke(service, "applyMoneyStorageWrite", new Class<?>[]{Map.class, AiCrudConfig.class}, data, config());

        assertEquals(1230L, data.get("cash_amount"));
    }

    @Test
    void convertsMinorUnitStorageToMajorUnitDisplay() throws Exception {
        DynamicCrudService service = service();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cash_amount", 1230L);

        invoke(service, "applyMoneyDisplayProjection", new Class<?>[]{List.class, AiCrudConfig.class}, List.of(row), config());

        assertEquals(new BigDecimal("12.30"), row.get("cash_amount"));
    }

    @Test
    void rejectsMoneyInputWithMoreFractionalDigitsThanConfigured() {
        DynamicCrudService service = service();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "12.345");

        assertThrows(BusinessException.class, () -> invokeUnchecked(
                service, "applyMoneyStorageWrite", new Class<?>[]{Map.class, AiCrudConfig.class}, data, config()));
    }

    @Test
    void rejectsMoneyInputBelowConfiguredMinimum() {
        DynamicCrudService service = service();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cash_amount", "0.00");

        assertThrows(BusinessException.class, () -> invokeUnchecked(
                service, "applyMoneyStorageWrite", new Class<?>[]{Map.class, AiCrudConfig.class}, data, config()));
    }

    @Test
    void leavesDecimalMoneyFieldsUntouchedForLegacyCompatibility() throws Exception {
        DynamicCrudService service = service();
        AiCrudConfig config = new AiCrudConfig();
        config.setModelSchema("""
                {"fields":[{"field":"amount","columnName":"amount","dataType":"decimal","componentType":"money","precision":2}]}
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", "12.30");

        invoke(service, "applyMoneyStorageWrite", new Class<?>[]{Map.class, AiCrudConfig.class}, data, config);

        assertEquals("12.30", data.get("amount"));
    }

    private DynamicCrudService service() {
        return new DynamicCrudService(
                null, null, objectMapper, null, null, null,
                new LowcodeEncryptConfigParser(objectMapper), null, null, null, null, null, null, null);
    }

    private AiCrudConfig config() {
        AiCrudConfig config = new AiCrudConfig();
        config.setModelSchema("""
                {"fields":[{"field":"cashAmount","columnName":"cash_amount","dataType":"bigint","componentType":"money","businessFieldType":"MONEY","precision":2,"basicProps":{"min":0.01}}]}
                """);
        return config;
    }

    private void invoke(DynamicCrudService service, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        invokeUnchecked(service, name, parameterTypes, args);
    }

    private void invokeUnchecked(DynamicCrudService service, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = DynamicCrudService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            method.invoke(service, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }
}
