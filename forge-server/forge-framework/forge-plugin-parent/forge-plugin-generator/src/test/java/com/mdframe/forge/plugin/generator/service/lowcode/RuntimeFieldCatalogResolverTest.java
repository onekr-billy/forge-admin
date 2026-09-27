package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeFieldCatalogResolverTest {

    @Test
    void explicitGridOrderAppendsManagedStatusFromOlderSnapshot() {
        LowcodeModelSchema model = model(field("name"), field("amount"), managedStatus());
        LowcodePageSchema page = new LowcodePageSchema();
        page.setListGridLayout(Map.of("items", List.of(Map.of(
                "blockType", "data-table", "fieldRefs", List.of("amount", "name")))));

        List<LowcodeFieldSchema> fields = RuntimeFieldCatalogResolver.resolveFields(
                model, page, "table", field -> true, (schema, code) -> false);

        assertEquals(List.of("amount", "name", "flowStatus"), names(fields));
    }

    @Test
    void explicitlyHiddenStatusAndInactiveFieldsDoNotLeakBack() {
        LowcodeFieldSchema inactive = field("archived");
        inactive.setFieldStatus("DISABLED");
        LowcodeModelSchema model = model(field("name"), inactive, managedStatus());
        LowcodePageSchema page = new LowcodePageSchema();
        page.setListGridLayout(Map.of("items", List.of(Map.of(
                "blockType", "data-table", "fieldRefs", List.of("name", "archived")))));

        List<LowcodeFieldSchema> fields = RuntimeFieldCatalogResolver.resolveFields(
                model, page, "table", field -> true,
                (schema, code) -> "flowStatus".equals(code));

        assertEquals(List.of("name"), names(fields));
    }

    @Test
    void searchUsesDedicatedAiCrudFieldRefsAndKeepsOrder() {
        LowcodeModelSchema model = model(field("name"), field("amount"));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setListGridLayout(Map.of("items", List.of(Map.of(
                "blockType", "AiCrudPage",
                "fieldRefs", List.of("amount", "name"),
                "props", Map.of("searchFieldRefs", List.of("name", "amount", "name"))))));

        List<LowcodeFieldSchema> fields = RuntimeFieldCatalogResolver.resolveFields(
                model, page, "search", field -> true, (schema, code) -> false);

        assertEquals(List.of("name", "amount"), names(fields));
    }

    @Test
    void missingGridSelectionFallsBackToActiveFields() {
        LowcodeFieldSchema inactive = field("old");
        inactive.setFieldStatus("HIDDEN");
        LowcodeModelSchema model = model(field("name"), inactive);

        List<LowcodeFieldSchema> fields = RuntimeFieldCatalogResolver.resolveFields(
                model, new LowcodePageSchema(), "table", field -> true, (schema, code) -> false);

        assertEquals(List.of("name"), names(fields));
    }

    private static LowcodeModelSchema model(LowcodeFieldSchema... fields) {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(fields));
        return model;
    }

    private static LowcodeFieldSchema field(String code) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(code);
        return field;
    }

    private static LowcodeFieldSchema managedStatus() {
        LowcodeFieldSchema field = field("flowStatus");
        field.setColumnName("flow_status");
        field.setListVisible(true);
        field.setDictType("business_flow_status");
        return field;
    }

    private static List<String> names(List<LowcodeFieldSchema> fields) {
        return fields.stream().map(LowcodeFieldSchema::getField).toList();
    }
}
