package com.mdframe.forge.plugin.generator.codegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.GenTable;
import com.mdframe.forge.plugin.generator.domain.entity.GenTableColumn;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.mapper.GenTableColumnMapper;
import com.mdframe.forge.plugin.generator.service.lowcode.GeneratedLowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeProtocolSnapshotBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VelocityRelatedTablePlannerTest {

    private final VelocityRelatedTablePlanner planner = new VelocityRelatedTablePlanner(
        new VelocityCodegenStrategy(
            mock(GenTableColumnMapper.class), new ObjectMapper(),
            mock(GeneratedLowcodeRuntimeConfigBuilder.class),
            mock(LowcodeProtocolSnapshotBuilder.class), mock(ObjectProvider.class)));

    @Test
    void pageRelationBackfillsMasterDetailKeysWhenDerivedConfigIsMissing() {
        AiCrudConfig config = new AiCrudConfig();
        config.setTableName("biz_order");
        config.setOptions("{}");
        GenTable mainTable = mainTable();
        LowcodePageSchema pageSchema = pageSchema();

        List<VelocityCodegenStrategy.RelatedTableMeta> related = planner.buildRelatedTables(
            config, pageSchema, mainTable, "order", "manage");
        List<VelocityCodegenStrategy.RelatedTableMeta> children = planner.buildMasterDetailChildren(
            new LinkedHashMap<>(), related, mainTable, pageSchema);

        assertEquals(1, children.size());
        VelocityCodegenStrategy.RelatedTableMeta child = children.get(0);
        assertTrue(child.isMasterDetailChild());
        assertEquals("orderId", child.getChildFkField());
        assertEquals("order_id", child.getChildFkColumn());
        assertEquals("id", child.getMainField());
    }

    @Test
    void separateTreeSourceIsInjectedOnlyOnce() {
        AiCrudConfig config = new AiCrudConfig();
        config.setTableName("biz_order");
        config.setOptions("{}");
        GenTable mainTable = mainTable();
        LowcodePageSchema pageSchema = pageSchema();
        List<VelocityCodegenStrategy.RelatedTableMeta> related = planner.buildRelatedTables(
            config, pageSchema, mainTable, "order", "manage");
        Map<String, Object> treeConfig = Map.of(
            "sourceModelCode", "order_item", "keyField", "id",
            "parentField", "orderId", "labelField", "name");

        VelocityCodegenStrategy.TreeCodegenMeta tree = planner.buildTreeMeta(
            mainTable, related, treeConfig);
        List<VelocityCodegenStrategy.RelatedTableMeta> injected =
            planner.resolveInjectedRelatedTables(tree, related, List.of(related.get(0)));

        assertTrue(tree.isSeparateSource());
        assertTrue(related.get(0).isTreeSource());
        assertEquals(1, injected.size());
        assertFalse(injected.get(0).getClassName().isBlank());
    }

    private GenTable mainTable() {
        GenTableColumn id = column("id", "id", true);
        GenTable table = new GenTable();
        table.setTableName("biz_order");
        table.setClassName("Order");
        table.setPackageName("com.example");
        table.setAuthor("test");
        table.setColumns(List.of(id));
        table.setPkColumn(id);
        return table;
    }

    private LowcodePageSchema pageSchema() {
        LowcodePageModelRef primary = new LowcodePageModelRef();
        primary.setPrimary(true);
        primary.setModelCode("order");
        primary.setTableName("biz_order");
        LowcodePageModelRef child = new LowcodePageModelRef();
        child.setPrimary(false);
        child.setModelCode("order_item");
        child.setModelName("订单明细");
        child.setTableName("biz_order_item");
        child.setFields(List.of(
            field("id", "id", "bigint", true),
            field("orderId", "order_id", "bigint", false),
            field("name", "name", "varchar", false)));
        LowcodeRelationSchema relation = new LowcodeRelationSchema();
        relation.setRelationType("CHILD_LIST");
        relation.setTargetObjectCode("order");
        relation.setSourceField("orderId");
        relation.setTargetField("id");
        child.setRelations(List.of(relation));
        LowcodePageSchema schema = new LowcodePageSchema();
        schema.setPrimaryModelCode("order");
        schema.setModelRefs(List.of(primary, child));
        return schema;
    }

    private Map<String, Object> field(String field, String column, String type, boolean primaryKey) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("field", field);
        value.put("columnName", column);
        value.put("dataType", type);
        value.put("primaryKey", primaryKey);
        return value;
    }

    private GenTableColumn column(String javaField, String columnName, boolean primaryKey) {
        GenTableColumn column = new GenTableColumn();
        column.setJavaField(javaField);
        column.setColumnName(columnName);
        column.setJavaType("Long");
        column.setIsPk(primaryKey ? 1 : 0);
        return column;
    }
}
