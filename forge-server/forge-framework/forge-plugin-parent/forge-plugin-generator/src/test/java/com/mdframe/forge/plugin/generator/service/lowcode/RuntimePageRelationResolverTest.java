package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimePageRelationResolverTest {

    @Test
    void infersChildForeignKeyOnlyWhenNoConfiguredRelationExists() {
        LowcodePageModelRef child = new LowcodePageModelRef();
        child.setModelCode("purchase_item");
        child.setFields(List.of(Map.of("sourceField", "purchaseOrderId", "columnName", "purchase_order_id")));

        LowcodeRelationSchema inferred = RuntimePageRelationResolver.resolveRuntimeRelation(
                "purchase_order", child, List.of());
        assertNotNull(inferred);
        assertEquals("ONE_TO_MANY", inferred.getRelationType());
        assertEquals("purchaseOrderId", inferred.getSourceField());
        assertEquals("purchase_order", inferred.getTargetObjectCode());

        LowcodeRelationSchema configured = new LowcodeRelationSchema();
        configured.setTargetObjectCode("purchase_item");
        configured.setSourceField("orderId");
        assertEquals(configured, RuntimePageRelationResolver.resolveRuntimeRelation(
                "purchase_order", child, List.of(configured)));
    }
}
