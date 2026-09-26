package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeChildTableCompilerTest {

    @Test
    @SuppressWarnings("unchecked")
    void preservesChildRelationSaveModeAndFieldSelection() {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setTableName("biz_order");
        LowcodePageModelRef primary = new LowcodePageModelRef();
        primary.setPrimary(true);
        primary.setModelCode("order");
        LowcodeRelationSchema relation = new LowcodeRelationSchema();
        relation.setTargetObjectCode("item");
        relation.setSourceField("id");
        relation.setTargetField("orderId");
        relation.setRelationType("ONE_TO_MANY");
        primary.setRelations(List.of(relation));

        LowcodePageModelRef child = new LowcodePageModelRef();
        child.setModelCode("item");
        child.setModelName("明细");
        child.setTableName("biz_order_item");
        child.setProps(Map.of(
                "saveMode", "merge",
                "inlineCreateEnabled", false,
                "rowActions", List.of(Map.of("key", "inspect"))));

        LowcodePageZone edit = new LowcodePageZone();
        edit.setZoneKey("edit");
        edit.setFieldRefs(List.of("item__quantity"));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("master-detail-crud");
        page.setPrimaryModelCode("order");
        page.setModelRefs(List.of(primary, child));
        page.setZones(List.of(edit));

        AtomicReference<List<String>> selectedRefs = new AtomicReference<>();
        AtomicReference<String> foreignKey = new AtomicReference<>();
        Map<String, Object> config = RuntimeChildTableCompiler.buildMasterDetailConfig(model, page,
                (ref, refs, fk) -> {
                    selectedRefs.set(refs);
                    foreignKey.set(fk);
                    return List.of(Map.of("field", "quantity"));
                });

        List<Map<String, Object>> children = (List<Map<String, Object>>) config.get("children");
        assertEquals(1, children.size());
        Map<String, Object> compiled = children.get(0);
        assertEquals(List.of("item__quantity"), selectedRefs.get());
        assertEquals("orderId", foreignKey.get());
        assertEquals(false, compiled.get("allowCreate"));
        assertEquals("merge", compiled.get("saveMode"));
        assertEquals("id", compiled.get("targetField"));
        assertEquals(List.of(Map.of("key", "inspect")), compiled.get("rowActions"));
        assertEquals(List.of(Map.of("field", "quantity")), compiled.get("fields"));
    }
}
