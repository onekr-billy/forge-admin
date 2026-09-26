package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("business flow task child permission policy")
class BusinessFlowTaskChildPolicyTest {

    private final BusinessFlowTaskChildPolicy policy = new BusinessFlowTaskChildPolicy();

    @Test
    @DisplayName("field permissions match child aliases and camel snake field names")
    void appliesAliasedFieldPermissions() {
        JSONObject nodeForm = nodeForm("""
                {"fields":[
                  {"field":"product_id","scope":"child","childKey":"order_item","childField":"product_id","readable":true,"writable":true,"required":true},
                  {"field":"secret","scope":"child","childKey":"order_item","childField":"secret","readable":false,"writable":false}
                ]}
                """);
        List<Map<String, Object>> fields = List.of(
                Map.of("field", "productId", "label", "商品"),
                Map.of("field", "secret", "label", "内部字段"));

        List<Map<String, Object>> result = policy.applyFieldPermissions(
                fields, nodeForm, "purchase_order_order_item");

        assertEquals(1, result.size());
        assertEquals("productId", result.get(0).get("field"));
        assertEquals(true, result.get(0).get("writable"));
        assertEquals(true, result.get(0).get("required"));
        assertEquals(false, result.get(0).get("readonly"));
    }

    @Test
    @DisplayName("explicit node permissions override published writable flags")
    void explicitPermissionsDriveSaveWhitelist() {
        JSONObject nodeForm = nodeForm("""
                {"fields":[
                  {"field":"quantity","scope":"child","childKey":"order_item","childField":"quantity","readable":true,"writable":true},
                  {"field":"price","scope":"child","childKey":"order_item","childField":"price","readable":true,"writable":false}
                ]}
                """);
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("modelCode", "purchase_order_order_item");
        child.put("readable", true);
        child.put("allowUpdate", true);
        child.put("fields", List.of(
                Map.of("field", "quantity", "writable", false),
                Map.of("field", "price", "writable", true)));

        Map<String, DynamicCrudService.TaskChildPermission> result =
                policy.buildSavePermissions(List.of(child), nodeForm);
        DynamicCrudService.TaskChildPermission permission = result.get("purchase_order_order_item");

        assertEquals(true, permission.allowUpdate());
        assertEquals(List.of("quantity"), permission.writableFields().stream().toList());
    }

    @Test
    @DisplayName("visible rows keep ids aliases and companion name fields")
    @SuppressWarnings("unchecked")
    void filtersVisibleChildRows() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("children", Map.of("purchase_order_order_item", List.of(Map.of(
                "id", 7L,
                "product_id", 10L,
                "productIdName", "键盘",
                "secret", "hidden"))));
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("modelCode", "order_item");
        child.put("fields", List.of(Map.of("field", "productId")));

        policy.filterVisibleRecordChildren(record, List.of(child));

        Map<String, Object> children = (Map<String, Object>) record.get("children");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) children.get("order_item");
        assertEquals(1, rows.size());
        assertEquals(7L, rows.get(0).get("id"));
        assertEquals(10L, rows.get(0).get("product_id"));
        assertEquals("键盘", rows.get(0).get("productIdName"));
        assertFalse(rows.get(0).containsKey("secret"));
        assertTrue(children.containsKey("purchase_order_order_item"));
    }

    @Test
    @DisplayName("missing child configuration removes child payload")
    void removesUnconfiguredChildren() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("children", Map.of("order_item", List.of(Map.of("id", 1L))));

        policy.filterVisibleRecordChildren(record, List.of());

        assertFalse(record.containsKey("children"));
    }

    @Test
    @DisplayName("request payload is split without leaking children into main")
    void splitsTaskPayload() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", "PO-1");
        data.put("children", Map.of("order_item", List.of(Map.of("quantity", 2))));

        assertEquals(Map.of("orderNo", "PO-1"), policy.extractMainPayload(data));
        assertTrue(policy.extractChildrenPayload(data).containsKey("order_item"));
    }

    private JSONObject nodeForm(String permissions) {
        JSONObject nodeForm = new JSONObject();
        nodeForm.put("fieldPermissions", permissions);
        return nodeForm;
    }
}
