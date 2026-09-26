package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeFieldComponentResolverTest {

    @Test
    void searchRespectsFieldTypeOverStalePageOverride() {
        LowcodeFieldSchema tree = field("parentId", "orgSelect");
        assertEquals("orgTreeSelect", RuntimeFieldComponentResolver.resolveSearchComponentType(
                tree, "eq", Map.of("componentType", "number")));

        LowcodeFieldSchema date = field("startDate", "date");
        assertEquals("daterange", RuntimeFieldComponentResolver.resolveSearchComponentType(
                date, "between", Map.of("type", "input")));
        assertEquals("number", RuntimeFieldComponentResolver.normalizeEditComponentType("money"));
        assertEquals("userSelect", RuntimeFieldComponentResolver.normalizeEditComponentType("userPicker"));
    }

    @Test
    void objectReferenceAdvancedSelectorDoesNotForcePopup() {
        LowcodeFieldSchema reference = field("customerId", "objectReference");
        reference.setBasicProps(Map.of("recordSelector", Map.of("searchFields", "name")));
        assertEquals("objectReference", RuntimeFieldComponentResolver.resolveEditComponentType(reference));
        assertEquals("recordSelector", RuntimeFieldComponentResolver.resolveEditComponentType(
                reference, Map.of("props", Map.of("selector", Map.of("mode", "popup")))));
    }

    @Test
    void dynamicSourceAddsCompanionLabelWithoutOverwritingConfiguredTarget() {
        Map<String, Object> props = new LinkedHashMap<>(Map.of("optionSource", Map.of("type", "list")));
        RuntimeFieldComponentResolver.ensureDynamicOptionSourceLabelValueField(props, "ownerId", "select");
        assertEquals("ownerIdName", props.get("labelValueField"));

        Map<String, Object> userProps = new LinkedHashMap<>(Map.of("targetField", "customLabel"));
        RuntimeFieldComponentResolver.applySelectionLabelProps(userProps, "ownerId", "userSelect");
        assertEquals("ownerIdName", userProps.get("labelValueField"));
        assertEquals("customLabel", userProps.get("targetField"));

        Map<String, Object> staticProps = new LinkedHashMap<>(Map.of("optionSource", Map.of("type", "STATIC")));
        RuntimeFieldComponentResolver.ensureDynamicOptionSourceLabelValueField(staticProps, "ownerId", "select");
        assertFalse(staticProps.containsKey("labelValueField"));
    }

    @Test
    void placeholdersAndFormSizeKeepExistingProtocol() {
        assertEquals("请选择审批人", RuntimeFieldComponentResolver.buildPlaceholder("userSelect", "审批人"));
        assertEquals("请输入标题", RuntimeFieldComponentResolver.buildPlaceholder("input", "标题"));
        assertEquals("medium", RuntimeFieldComponentResolver.normalizeRuntimeFormSize("default"));
        assertEquals("large", RuntimeFieldComponentResolver.normalizeRuntimeFormSize("LARGE"));
    }

    private LowcodeFieldSchema field(String code, String type) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(code);
        field.setComponentType(type);
        return field;
    }
}
