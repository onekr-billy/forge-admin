package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFormServiceImplFieldCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlowFormServiceImpl service = new FlowFormServiceImpl(null, null, objectMapper);

    @Test
    void buildsArrayParentAndScopedItemFields() throws Exception {
        String schema = """
                [{
                  "type":"group",
                  "field":"expenseItems",
                  "title":"费用明细",
                  "props":{"rule":[
                    {"type":"input","field":"name","title":"名称"},
                    {"type":"inputNumber","field":"amount","title":"金额"}
                  ]}
                },{
                  "type":"group",
                  "field":"goods",
                  "title":"商品明细",
                  "props":{"rule":[
                    {"type":"input","field":"name","title":"商品名称"}
                  ]}
                }]
                """;

        List<Map<String, Object>> catalog = objectMapper.readValue(
                service.buildFieldRegistryJson(schema),
                new TypeReference<>() {
                });

        assertTrue(catalog.stream().anyMatch(item -> "expenseItems".equals(item.get("field"))
                && "array".equals(item.get("dataType"))));
        assertTrue(catalog.stream().anyMatch(item -> "array".equals(item.get("scope"))
                && "expenseItems".equals(item.get("arrayKey"))
                && "name".equals(item.get("itemField"))));
        assertTrue(catalog.stream().anyMatch(item -> "array".equals(item.get("scope"))
                && "goods".equals(item.get("arrayKey"))
                && "name".equals(item.get("itemField"))));
        assertEquals(5, catalog.size());
    }
}
