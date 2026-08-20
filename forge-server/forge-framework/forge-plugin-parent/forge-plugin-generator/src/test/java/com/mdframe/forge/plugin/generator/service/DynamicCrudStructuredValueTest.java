package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicCrudStructuredValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesStructuredFieldArraysAsJson() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active_period", List.of("2026-08-01", "2026-08-31"));

        invoke("applyStructuredFieldStorageWrite", new Class<?>[]{Map.class, AiCrudConfig.class}, data, config());

        assertEquals("[\"2026-08-01\",\"2026-08-31\"]", data.get("active_period"));
    }

    @Test
    void restoresJsonAndLegacyCommaSeparatedValuesAsArrays() throws Exception {
        Map<String, Object> jsonRow = new LinkedHashMap<>();
        jsonRow.put("activePeriod", "[\"2026-08-01\",\"2026-08-31\"]");
        Map<String, Object> legacyRow = new LinkedHashMap<>();
        legacyRow.put("tags", "draft,approved");

        invoke("applyStructuredFieldDisplayProjection", new Class<?>[]{List.class, AiCrudConfig.class},
                List.of(jsonRow, legacyRow), config());

        assertEquals(List.of("2026-08-01", "2026-08-31"), jsonRow.get("activePeriod"));
        assertEquals(List.of("draft", "approved"), legacyRow.get("tags"));
    }

    private AiCrudConfig config() {
        AiCrudConfig config = new AiCrudConfig();
        config.setModelSchema("""
                {
                  "fields": [
                    {
                      "field": "activePeriod",
                      "columnName": "active_period",
                      "dataType": "text",
                      "componentType": "daterange"
                    },
                    {
                      "field": "tags",
                      "columnName": "tags",
                      "dataType": "varchar",
                      "componentType": "checkbox"
                    }
                  ]
                }
                """);
        return config;
    }

    private void invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        DynamicCrudService service = new DynamicCrudService(
                null, null, objectMapper, null, null, null,
                new LowcodeEncryptConfigParser(objectMapper), null, null, null, null, null, null, null);
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
