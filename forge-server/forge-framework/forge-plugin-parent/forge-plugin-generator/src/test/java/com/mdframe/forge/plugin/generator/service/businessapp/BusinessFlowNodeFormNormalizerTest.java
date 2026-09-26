package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowNodeFormNormalizerTest {

    @Test
    void objectStringRetainsMainAndChildPermissions() {
        String permissions = """
                {"version":2,"fields":[
                  {"field":"amount","readable":true,"writable":false,"required":true},
                  {"field":"qty","scope":"child","childKey":"items","childField":"qty","writable":true,"required":true}
                ]}
                """;

        List<Map<String, Object>> fields = BusinessFlowNodeFormNormalizer.normalizeFieldPermissions(permissions);

        assertEquals(2, fields.size());
        assertEquals("amount", fields.get(0).get("field"));
        assertEquals(false, fields.get(0).get("writable"));
        assertEquals(false, fields.get(0).get("required"));
        assertEquals("child", fields.get(1).get("scope"));
        assertEquals("items", fields.get(1).get("childKey"));
        assertEquals(true, fields.get(1).get("required"));
    }

    @Test
    void childPermissionsKeepRowActionsAndDeduplicateChildKey() {
        String permissions = """
                {"children":[
                  {"childKey":"items","readable":true,"allowCreate":true,"allowUpdate":false,"allowDelete":false},
                  {"childKey":"items","allowDelete":true}
                ]}
                """;

        List<Map<String, Object>> children = BusinessFlowNodeFormNormalizer.normalizeTaskChildPermissions(permissions);

        assertEquals(1, children.size());
        assertEquals(true, children.get(0).get("allowCreate"));
        assertEquals(false, children.get(0).get("allowUpdate"));
        assertEquals(false, children.get(0).get("allowDelete"));
    }

    @Test
    void fieldSelectionSetsDoNotGrantWriteOrRequiredWithoutRead() {
        Map<String, Object> selection = Map.of(
                "visibleFields", List.of("name"),
                "writableFields", List.of("name", "secret"),
                "requiredFields", List.of("secret"));

        List<Map<String, Object>> fields = BusinessFlowNodeFormNormalizer.normalizeFieldPermissions(selection);

        assertEquals(2, fields.size());
        assertEquals(true, fields.get(0).get("writable"));
        assertEquals(false, fields.get(0).get("required"));
        assertEquals(false, fields.get(1).get("readable"));
        assertEquals(false, fields.get(1).get("writable"));
        assertEquals(false, fields.get(1).get("required"));
    }

    @Test
    void duplicateNodeKeepsFirstBindingAndNormalizesModes() {
        List<Map<String, Object>> nodes = List.of(
                Map.of("taskDefKey", "approve", "formMode", "business_code_form",
                        "editMode", "editable", "fieldPermissions", List.of(Map.of("field", "name"))),
                Map.of("taskDefKey", "approve", "formMode", "external"));

        List<Map<String, Object>> result = BusinessFlowNodeFormNormalizer.normalizeNodeForms(nodes);

        assertEquals(1, result.size());
        assertEquals("BUSINESS_CODE_FORM", result.get(0).get("formMode"));
        assertEquals("EDITABLE", result.get(0).get("editMode"));
        assertFalse(((List<?>) result.get(0).get("fieldPermissions")).isEmpty());
    }

    @Test
    void malformedJsonAndBooleanCompatibilityRemainFailClosed() {
        assertTrue(BusinessFlowJsonReader.readNestedArray("invalid").isEmpty());
        assertTrue(BusinessFlowJsonReader.readNestedObject("invalid").isEmpty());
        assertTrue(BusinessFlowJsonReader.readBooleanValue("yes", false));
        assertFalse(BusinessFlowJsonReader.readBooleanValue("0", true));
    }
}
