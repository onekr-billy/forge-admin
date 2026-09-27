package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimePageRefFieldFactoryTest {

    @Test
    void decodesChildSnapshotWithReferencePropsAndReadableLabel() {
        LowcodePageModelRef child = new LowcodePageModelRef();
        child.setModelCode("order-item");
        child.setModelName("明细");

        LowcodeFieldSchema field = RuntimePageRefFieldFactory.build(child, Map.of(
                "sourceField", "materialId",
                "columnName", "material_id",
                "label", "明细 · 物料",
                "dataType", "bigint",
                "length", "24",
                "readonly", 1,
                "referenceObjectCode", "material",
                "referenceDisplayField", "name",
                "basicProps", Map.of("optionSource", Map.of("type", "REMOTE")),
                "advancedProps", Map.of("managedBy", "reference")));

        assertEquals("order_item__materialId", field.getField());
        assertEquals("物料", field.getLabel());
        assertEquals(24, field.getLength());
        assertEquals(true, field.getReadonly());
        assertEquals("material", field.getBasicProps().get("referenceObjectCode"));
        assertEquals("name", field.getBasicProps().get("referenceDisplayField"));
        assertEquals("reference", field.getAdvancedProps().get("managedBy"));
    }

    @Test
    void keepsPrimaryFieldNameAndRejectsMissingSource() {
        LowcodePageModelRef primary = new LowcodePageModelRef();
        primary.setPrimary(true);
        primary.setModelCode("order");

        assertEquals("orderCode", RuntimePageRefFieldFactory.build(primary, Map.of(
                "field", "orderCode", "label", "订单编号")).getField());
        assertNull(RuntimePageRefFieldFactory.build(primary, Map.of("label", "无字段")));
    }
}
