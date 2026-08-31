package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务对象发布检查 - 表单组件树")
@Tag("dev")
class BusinessObjectPublishServiceFormSchemaTest {

    private BusinessObjectPublishService service;
    private Method checkFormDesignerSchema;

    @BeforeEach
    void setUp() throws Exception {
        service = new BusinessObjectPublishService(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, new ObjectMapper());
        checkFormDesignerSchema = BusinessObjectPublishService.class.getDeclaredMethod(
                "checkFormDesignerSchema", Map.class, Set.class, List.class);
        checkFormDesignerSchema.setAccessible(true);
    }

    @Test
    @DisplayName("4 列栅格内绑定的业务字段可以通过表单发布检查")
    void acceptsBoundFieldsNestedInRowColumns() throws Exception {
        Map<String, Object> schema = formSchema(List.of(row(List.of(
                field("cmp_fieldInput", "input", "店铺", "fieldInput"),
                field("cmp_fieldDictSelect", "dictSelect", "状态", "fieldDictSelect"),
                field("cmp_fieldDictSelect2", "dictSelect", "平台", "fieldDictSelect2"),
                field("cmp_fieldInput2", "input", "备注", "fieldInput2")))));

        List<BusinessPublishCheckItemVO> items = validate(schema, Set.of(
                "fieldInput", "fieldDictSelect", "fieldDictSelect2", "fieldInput2"));

        assertTrue(items.stream().noneMatch(item ->
                "FORM_FIELD_COMPONENT_EMPTY".equals(item.getItemCode())
                        || BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())));
    }

    @Test
    @DisplayName("仅有空栅格、没有绑定字段时仍阻断发布")
    void rejectsEmptyRowWithoutFieldComponents() throws Exception {
        Map<String, Object> schema = formSchema(List.of(row(List.of())));

        List<BusinessPublishCheckItemVO> items = validate(schema, Set.of("fieldInput"));

        assertTrue(items.stream().anyMatch(item ->
                "FORM_FIELD_COMPONENT_EMPTY".equals(item.getItemCode())
                        && BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())));
    }

    @Test
    @DisplayName("嵌套字段引用不存在编码时仍阻断发布")
    void rejectsNestedFieldMissingFromModel() throws Exception {
        Map<String, Object> schema = formSchema(List.of(row(List.of(
                field("cmp_fieldInput", "input", "店铺", "missingField")))));

        List<BusinessPublishCheckItemVO> items = validate(schema, Set.of("fieldInput"));

        assertTrue(items.stream().anyMatch(item ->
                "FORM_FIELD_MISSING".equals(item.getItemCode())
                        && "missingField".equals(item.getFieldCode())));
        assertTrue(items.stream().noneMatch(item -> "FORM_FIELD_COMPONENT_EMPTY".equals(item.getItemCode())));
    }

    @Test
    @DisplayName("嵌套字段未绑定编码时仍阻断发布")
    void rejectsNestedFieldWithoutBinding() throws Exception {
        Map<String, Object> unbound = field("cmp_unbound", "input", "店铺", "");
        Map<String, Object> schema = formSchema(List.of(row(List.of(unbound))));

        List<BusinessPublishCheckItemVO> items = validate(schema, Set.of("fieldInput"));

        assertTrue(items.stream().anyMatch(item -> "FORM_FIELD_BINDING_EMPTY".equals(item.getItemCode())));
        assertTrue(items.stream().anyMatch(item -> "FORM_FIELD_COMPONENT_EMPTY".equals(item.getItemCode())));
    }

    @Test
    @DisplayName("顶层字段组件仍然按原规则通过")
    void acceptsTopLevelBoundField() throws Exception {
        Map<String, Object> schema = formSchema(List.of(
                field("cmp_fieldInput", "input", "店铺", "fieldInput")));

        List<BusinessPublishCheckItemVO> items = validate(schema, Set.of("fieldInput"));

        assertTrue(items.stream().noneMatch(item -> BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())));
    }

    @SuppressWarnings("unchecked")
    private List<BusinessPublishCheckItemVO> validate(Map<String, Object> formSchema, Set<String> modelFields)
            throws Exception {
        List<BusinessPublishCheckItemVO> items = new ArrayList<>();
        checkFormDesignerSchema.invoke(service, formSchema, modelFields, items);
        return items;
    }

    private Map<String, Object> formSchema(List<Map<String, Object>> components) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("schemaVersion", "1");
        schema.put("formKey", "main");
        schema.put("components", components);
        return schema;
    }

    private Map<String, Object> row(List<Map<String, Object>> fields) {
        List<Map<String, Object>> fieldList = fields == null ? List.of() : fields;
        List<Map<String, Object>> columns = new ArrayList<>();
        int columnCount = Math.max(fieldList.size(), 1);
        for (int index = 0; index < columnCount; index++) {
            Map<String, Object> col = component("cmp_row_col_" + (index + 1), "col", "第 " + (index + 1) + " 列", "virtual", "");
            List<Map<String, Object>> children = new ArrayList<>();
            if (index < fieldList.size()) {
                children.add(fieldList.get(index));
            }
            col.put("children", children);
            columns.add(col);
        }
        Map<String, Object> row = component("cmp_row", "row", "4 列栅格", "virtual", "");
        row.put("children", columns);
        return row;
    }

    private Map<String, Object> field(String id, String componentKey, String label, String fieldCode) {
        return component(id, componentKey, label, "field", fieldCode);
    }

    private Map<String, Object> component(String id, String componentKey, String label, String mode, String fieldCode) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("id", id);
        component.put("componentKey", componentKey);
        component.put("label", label);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("mode", mode);
        binding.put("fieldCode", fieldCode);
        component.put("fieldBinding", binding);
        component.put("children", List.of());
        return component;
    }
}
