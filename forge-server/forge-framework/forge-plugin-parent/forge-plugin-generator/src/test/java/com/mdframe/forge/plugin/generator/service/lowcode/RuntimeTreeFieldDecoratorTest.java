package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTreeFieldDecoratorTest {

    @Test
    void leftTreeStillUsesCurrentObjectForItsOwnParentField() {
        LowcodeModelSchema model = modelWithParent();
        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("tree-crud");
        Map<String, Object> parent = new LinkedHashMap<>(Map.of("field", "parentId", "label", "父级", "type", "input"));
        List<Map<String, Object>> fields = new ArrayList<>(List.of(parent));

        RuntimeTreeFieldDecorator.decorate(fields, "order-runtime", model, page, true, false);

        assertEquals("treeSelect", parent.get("type"));
        assertEquals("eq", parent.get("queryType"));
        Map<?, ?> optionSource = (Map<?, ?>) parent.get("optionSource");
        assertEquals("get@/ai/crud/order-runtime/tree", optionSource.get("api"));
        assertEquals(optionSource, ((Map<?, ?>) parent.get("props")).get("optionSource"));
    }

    @Test
    void existingOptionSourceIsNotReplaced() {
        LowcodeModelSchema model = modelWithParent();
        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("tree-crud");
        Map<String, Object> existing = Map.of("type", "list", "api", "get@/external/options");
        Map<String, Object> parent = new LinkedHashMap<>(Map.of(
                "field", "parentId", "type", "input", "optionSource", existing));

        RuntimeTreeFieldDecorator.decorate(new ArrayList<>(List.of(parent)), "order-runtime",
                model, page, true, false);

        assertEquals("treeSelect", parent.get("type"));
        assertEquals(existing, parent.get("optionSource"));
        assertFalse(parent.containsKey("queryType"));
    }

    @Test
    void missingParentFieldDoesNotInventSelfTreeApi() {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(field("name")));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("tree-crud");
        Map<String, Object> selector = new LinkedHashMap<>(Map.of("field", "name", "type", "treeSelect"));

        RuntimeTreeFieldDecorator.decorate(new ArrayList<>(List.of(selector)), "order-runtime",
                model, page, true, false);

        assertTrue(!selector.containsKey("optionSource"));
    }

    private LowcodeModelSchema modelWithParent() {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(field("parentId")));
        LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
        treeConfig.setParentField("parentId");
        model.setTreeConfig(treeConfig);
        return model;
    }

    private LowcodeFieldSchema field(String code) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(code);
        return field;
    }
}
