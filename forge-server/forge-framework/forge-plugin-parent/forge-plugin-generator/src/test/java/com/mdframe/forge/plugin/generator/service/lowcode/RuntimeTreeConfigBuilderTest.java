package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RuntimeTreeConfigBuilderTest {

    @Test
    void treePanelSourceWinsOverStaleTableZoneAndNormalizesFieldNames() {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setBusinessName("订单");
        LowcodeFieldSchema orgId = new LowcodeFieldSchema();
        orgId.setField("orgId");
        orgId.setColumnName("org_id");
        model.setFields(List.of(orgId));

        LowcodePageModelRef primary = new LowcodePageModelRef();
        primary.setPrimary(true);
        primary.setModelCode("order");
        LowcodeRelationSchema relation = new LowcodeRelationSchema();
        relation.setTargetObjectCode("org");
        relation.setSourceField("orgId");
        relation.setTargetField("id");
        primary.setRelations(List.of(relation));

        LowcodePageModelRef source = new LowcodePageModelRef();
        source.setModelCode("org");
        source.setModelName("组织");
        source.setFields(List.of(
                Map.of("sourceField", "id", "columnName", "org_id"),
                Map.of("sourceField", "name", "columnName", "org_name")));

        LowcodePageZone table = new LowcodePageZone();
        table.setZoneKey("table");
        table.setProps(Map.of("treeConfig", Map.of("sourceConfigKey", "")));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("tree-crud");
        page.setPrimaryModelCode("order");
        page.setModelRefs(List.of(primary, source));
        page.setZones(List.of(table));
        Map<String, Object> panelProps = Map.of(
                "sourceModelCode", "org",
                "sourceConfigKey", "org-runtime",
                "keyField", "org_id",
                "labelField", "org_name",
                "filterField", "org_id");
        page.setListGridLayout(Map.of("items", List.of(Map.of("blockType", "tree-panel", "props", panelProps))));

        Object overrides = RuntimeTreeConfigBuilder.extractTreeConfigOverrides(page);
        assertSame(panelProps, overrides);
        Map<String, Object> config = RuntimeTreeConfigBuilder.buildTreeConfig(model, page, overrides);
        assertEquals("org-runtime", config.get("sourceConfigKey"));
        assertEquals("id", config.get("keyField"));
        assertEquals("name", config.get("labelField"));
        assertEquals("orgId", config.get("filterField"));
        assertEquals("id", config.get("targetField"));
        assertEquals(true, config.get("enabled"));
        assertEquals(true, config.get("includeChildren"));
        assertEquals("org-runtime", RuntimeTreeConfigBuilder.resolveTreeApiConfigKey("order-runtime", page));
    }

    @Test
    void embeddedTreeListForcesEnabledWhenModelIsTreeEvenIfFlagMissing() {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setAppType("TREE");
        model.setBusinessName("分类");
        LowcodeTreeConfig tree = new LowcodeTreeConfig();
        tree.setParentField("parentId");
        tree.setLabelField("name");
        // 历史数据常只有 appType=TREE，enabled 为空
        model.setTreeConfig(tree);

        LowcodePageSchema page = new LowcodePageSchema();
        page.setLayoutType("list-form");
        LowcodePageZone table = new LowcodePageZone();
        table.setZoneKey("table");
        table.setProps(Map.of("treeConfig", tree));
        page.setZones(List.of(table));

        Map<String, Object> config = RuntimeTreeConfigBuilder.buildTreeConfig(
                model, page, RuntimeTreeConfigBuilder.extractTreeConfigOverrides(page));
        assertEquals(true, config.get("enabled"));
        assertEquals("parentId", config.get("parentField"));
        assertEquals("name", config.get("labelField"));
    }

    @Test
    void optionSourceKeepsTheExistingRuntimeProtocol() {
        Map<String, Object> source = RuntimeTreeConfigBuilder.buildTreeOptionSource(
                "org-runtime", Map.of("childrenField", "nodes"), Map.of("orderByColumn", "sort_no", "isAsc", "asc"));
        assertEquals(Map.of(
                "type", "tree",
                "api", "get@/ai/crud/org-runtime/tree",
                "keyField", "key",
                "valueField", "targetValue",
                "labelField", "label",
                "childrenField", "nodes",
                "params", Map.of("loadMode", "full", "orderByColumn", "sort_no", "isAsc", "asc")), source);
    }
}
