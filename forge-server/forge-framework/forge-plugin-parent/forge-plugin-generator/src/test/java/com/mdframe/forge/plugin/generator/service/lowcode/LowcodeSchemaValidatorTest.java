package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeObjectSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LowcodeSchemaValidatorTest {

    private final LowcodeSchemaValidator validator = new LowcodeSchemaValidator();

    @Test
    void validatesCharacterCapacityBeforeDdl() {
        LowcodeFieldSchema field = field("title", "varchar");
        field.setLength(2048);
        assertDoesNotThrow(() -> validator.validateModel(model(field)));

        field.setLength(2049);
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));
    }

    @Test
    void validatesDecimalDigitsAndConfiguredRange() {
        LowcodeFieldSchema field = field("amount", "decimal");
        field.setLength(10);
        field.setPrecision(2);
        field.setBasicProps(Map.of("min", 0, "max", 100));
        assertDoesNotThrow(() -> validator.validateModel(model(field)));

        field.setPrecision(10);
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));

        field.setPrecision(2);
        field.setBasicProps(Map.of("min", 101, "max", 100));
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));
    }

    @Test
    void acceptsStringAliasAsVarchar() {
        LowcodeFieldSchema field = field("title", "string");
        assertDoesNotThrow(() -> validator.validateModel(model(field)));
        assertEquals("varchar", field.getDataType());
    }

    @Test
    void acceptsBusinessNumberAliasAsDecimal() {
        LowcodeFieldSchema field = field("amount", "number");
        field.setComponentType("number");
        assertDoesNotThrow(() -> validator.validateModel(model(field)));
        assertEquals("decimal", field.getDataType());
    }

    @Test
    void allowsLeftTreeRightTableWithExternalTreeSource() {
        LowcodeModelSchema modelSchema = model(
                field("categoryId", "bigint"),
                field("orderName", "varchar")
        );
        LowcodeObjectSchema object = new LowcodeObjectSchema();
        object.setCode("business_object_zl97");
        modelSchema.setObject(object);
        modelSchema.setTableName("biz_order");

        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("tree-crud");
        pageSchema.setZones(List.of(tableZone()));
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("items", List.of(Map.of(
                "blockType", "tree-panel",
                "props", Map.of(
                        "sourceModelCode", "category_tree",
                        "sourceConfigKey", "category_tree",
                        "keyField", "categoryId",
                        "parentField", "parentCategoryId",
                        "labelField", "categoryName",
                        "targetField", "categoryId",
                        "filterField", "categoryId"
                )
        )));
        pageSchema.setListGridLayout(layout);

        assertDoesNotThrow(() -> validator.validatePage(pageSchema, modelSchema));
    }

    @Test
    void allowsTreePanelEvenWhenLayoutTypeNotYetTreeCrud() {
        LowcodeModelSchema modelSchema = model(
                field("categoryId", "bigint"),
                field("orderName", "varchar")
        );
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("simple-crud");
        pageSchema.setZones(List.of(tableZone()));
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("items", List.of(Map.of(
                "blockType", "tree-panel",
                "props", Map.of(
                        "sourceModelCode", "category_tree",
                        "parentField", "parentCategoryId",
                        "labelField", "categoryName",
                        "filterField", "categoryId"
                )
        )));
        pageSchema.setListGridLayout(layout);

        assertDoesNotThrow(() -> validator.validatePage(pageSchema, modelSchema));
    }

    @Test
    void allowsTreeCrudDraftWithoutTreeSourceConfigured() {
        LowcodeModelSchema modelSchema = model(
                field("orderName", "varchar")
        );
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("tree-crud");
        pageSchema.setZones(List.of(tableZone()));
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("items", List.of(Map.of(
                "blockType", "tree-panel",
                "props", Map.of("treeTitle", "筛选树")
        )));
        pageSchema.setListGridLayout(layout);

        assertDoesNotThrow(() -> validator.validatePage(pageSchema, modelSchema));
    }

    @Test
    void ignoresStaleAppTypeTreeWhenModelTreeDisabled() {
        // 左树右表关掉「添加下级」后常残留 appType=TREE + parentField=parentId
        LowcodeModelSchema modelSchema = model(
                field("categoryId", "bigint"),
                field("orderName", "varchar")
        );
        modelSchema.setAppType("TREE");
        LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
        treeConfig.setEnabled(false);
        treeConfig.setParentField("parentId");
        modelSchema.setTreeConfig(treeConfig);

        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("tree-crud");
        pageSchema.setZones(List.of(tableZone()));
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("items", List.of(Map.of(
                "blockType", "tree-panel",
                "props", Map.of(
                        "sourceModelCode", "category_tree",
                        "parentField", "parentCategoryId",
                        "filterField", "categoryId"
                )
        )));
        pageSchema.setListGridLayout(layout);

        assertDoesNotThrow(() -> validator.validatePage(pageSchema, modelSchema));
    }

    @Test
    void stillRequiresParentFieldForEmbeddedTreeTable() {
        LowcodeModelSchema modelSchema = model(
                field("name", "varchar")
        );
        LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
        treeConfig.setEnabled(true);
        treeConfig.setParentField("parentId");
        treeConfig.setLabelField("name");
        modelSchema.setTreeConfig(treeConfig);

        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("simple-crud");
        pageSchema.setZones(List.of(tableZone()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> validator.validatePage(pageSchema, modelSchema));
        assertEquals("树形父级字段不存在: parentId", error.getMessage());
    }

    private LowcodePageZone tableZone() {
        LowcodePageZone zone = new LowcodePageZone();
        zone.setZoneKey("table");
        zone.setFieldRefs(new ArrayList<>());
        zone.setProps(new LinkedHashMap<>());
        return zone;
    }

    private LowcodeFieldSchema field(String fieldName, String dataType) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldName);
        field.setColumnName(fieldName);
        field.setLabel("测试字段");
        field.setDataType(dataType);
        field.setComponentType("decimal".equals(dataType) || "number".equals(dataType) || "bigint".equals(dataType)
                ? "number" : "input");
        return field;
    }

    private LowcodeModelSchema model(LowcodeFieldSchema... fields) {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setAppType("SINGLE");
        model.setTableMode("EXISTING");
        model.setTableName("field_constraint_test");
        model.setFields(List.of(fields));
        return model;
    }
}
