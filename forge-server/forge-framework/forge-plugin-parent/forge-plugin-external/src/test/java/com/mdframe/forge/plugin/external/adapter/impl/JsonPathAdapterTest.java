package com.mdframe.forge.plugin.external.adapter.impl;

import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPathAdapterTest {

    private final JsonPathAdapter adapter = new JsonPathAdapter();

    @Test
    void shouldExtractAndMapOnlyDeclaredFields() {
        Object source = JSON.parse("""
                {"payload":{"items":[
                  {"id":"M1","profile":{"name":"会员甲"},"secret":"never-copy"},
                  {"id":"M2","profile":{"name":"会员乙"},"secret":"never-copy"}
                ]}}
                """);
        String config = """
                {"version":"FIELD_MAP_V1","sourcePath":"payload.items",
                 "fieldMapping":{"memberId":"id","memberName":"profile.name"},
                 "targetPath":"records"}
                """;

        Object transformed = adapter.transform(source, config);

        Map<?, ?> envelope = assertInstanceOf(Map.class, transformed);
        assertEquals(0, envelope.get("code"));
        Map<?, ?> data = assertInstanceOf(Map.class, envelope.get("data"));
        List<?> records = assertInstanceOf(List.class, data.get("records"));
        assertEquals(2, records.size());
        Map<?, ?> first = assertInstanceOf(Map.class, records.get(0));
        assertEquals(Map.of("memberId", "M1", "memberName", "会员甲"), first);
        assertFalse(first.containsKey("secret"));
    }

    @Test
    void shouldRejectExecutableUnknownAndUnboundedConfigurations() {
        assertFalse(adapter.validateConfig("while (true) { response.value++; }"));
        assertFalse(adapter.validateConfig("""
                {"version":"FIELD_MAP_V1","function":"fetch('https://example.com')"}
                """));
        assertFalse(adapter.validateConfig("""
                {"version":"FIELD_MAP_V1","sourcePath":"$..*","targetPath":"records"}
                """));
        assertFalse(adapter.validateConfig("{\"version\":\"FIELD_MAP_V1\",\"sourcePath\":\""
                + "a".repeat(17_000) + "\"}"));
        assertTrue(adapter.validateConfig("""
                {"version":"FIELD_MAP_V1","sourcePath":"payload.0","targetPath":"record"}
                """));
    }

    @Test
    void shouldBoundMappingOperationsAndResultSize() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int index = 0; index < 10_001; index++) {
            records.add(Map.of("id", index));
        }
        Map<String, Object> source = Map.of("items", records);
        String mappingConfig = """
                {"version":"FIELD_MAP_V1","sourcePath":"items",
                 "fieldMapping":{"id":"id"},"targetPath":"records"}
                """;
        String noMappingConfig = """
                {"version":"FIELD_MAP_V1","sourcePath":"items","targetPath":"records"}
                """;

        assertThrows(BusinessException.class, () -> adapter.transform(source, mappingConfig));
        assertThrows(BusinessException.class, () -> adapter.transform(source, noMappingConfig));

        Map<String, Object> oversized = new LinkedHashMap<>();
        oversized.put("payload", "x".repeat(2 * 1024 * 1024));
        String sizeConfig = """
                {"version":"FIELD_MAP_V1","sourcePath":"payload","targetPath":"value"}
                """;
        assertThrows(BusinessException.class, () -> adapter.transform(oversized, sizeConfig));
    }
}
