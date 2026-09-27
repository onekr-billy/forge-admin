package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessApplicationTreePageProjectorTest {

    private final BusinessApplicationTreePageProjector projector =
            new BusinessApplicationTreePageProjector();

    @Test
    void treeListProjectsBuilderConfigAndPreservesExistingModelValues() {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        LowcodeModelSchema model = new LowcodeModelSchema();
        LowcodeTreeConfig existing = new LowcodeTreeConfig();
        existing.setKeyField("businessId");
        model.setTreeConfig(existing);
        Map<String, Object> builder = builder(Map.of(
                "parentField", "parentBusinessId",
                "labelField", "displayName",
                "loadMode", "lazy"));

        projector.applyDesignerPreset(
                designer, model, null, "tree-list", "组织", builder, "page-1");

        assertSame(model, designer.getModelSchema());
        assertEquals("TREE", model.getAppType());
        assertTrue(model.getTreeConfig().getEnabled());
        assertEquals("businessId", model.getTreeConfig().getKeyField());
        assertEquals("parentBusinessId", model.getTreeConfig().getParentField());
        assertEquals("displayName", model.getTreeConfig().getLabelField());
        assertEquals("lazy", model.getTreeConfig().getLoadMode());
        assertEquals("组织树", model.getTreeConfig().getTreeTitle());
        assertEquals("list-form", designer.getPageSchema().getLayoutType());
        assertEquals(Boolean.TRUE, designer.getPageSchema().getZones().get(0)
                .getProps().get("enableTreeAddChild"));
        Map<?, ?> zoneTree = (Map<?, ?>) designer.getPageSchema().getZones().get(0)
                .getProps().get("treeConfig");
        assertEquals(Boolean.TRUE, zoneTree.get("enabled"));
        assertEquals("parentBusinessId", zoneTree.get("parentField"));
    }

    @Test
    void treeTableProjectsLayoutAndBuilderOptions() {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        Map<String, Object> builder = builder(Map.of("parentField", "pid"));

        projector.applyDesignerPreset(
                designer, null, pageSchema, "tree-table", "目录", builder, "page-1");
        projector.patchBuilder(builder, "page-1", "tree-table");

        assertSame(pageSchema, designer.getPageSchema());
        assertEquals("tree-crud", pageSchema.getLayoutType());
        assertEquals(Boolean.FALSE, pageSchema.getZones().get(0)
                .getProps().get("enableTreeAddChild"));
        Map<String, Object> props = blockProps(builder);
        assertEquals("tree-crud", props.get("layoutType"));
        assertEquals(Boolean.FALSE, props.get("enableTreeAddChild"));
        Map<String, Object> treeConfig = map(props.get("treeConfig"));
        assertEquals(Boolean.TRUE, treeConfig.get("enabled"));
        assertEquals("pid", treeConfig.get("parentField"));
        Map<String, Object> options = map(props.get("options"));
        assertEquals("tree-crud", options.get("layoutType"));
        assertEquals(Boolean.FALSE, options.get("enableTreeAddChild"));
    }

    @Test
    void treeListCorrectsAppTypeLeakedAsLayoutType() {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType("SINGLE");

        projector.applyDesignerPreset(
                designer, null, pageSchema, "tree-list", "分类", builder(Map.of()), "page-1");

        assertEquals("list-form", designer.getPageSchema().getLayoutType());
        Map<?, ?> zoneTree = (Map<?, ?>) designer.getPageSchema().getZones().get(0)
                .getProps().get("treeConfig");
        assertEquals(Boolean.TRUE, zoneTree.get("enabled"));
    }

    @Test
    void nonTreePageIsNotProjected() {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        Map<String, Object> builder = builder(Map.of());
        Map<String, Object> props = blockProps(builder);

        projector.applyDesignerPreset(
                designer, null, null, "list-form", "客户", builder, "page-1");
        projector.patchBuilder(builder, "page-1", "list-form");

        assertNull(designer.getModelSchema());
        assertNull(designer.getPageSchema());
        assertFalse(props.containsKey("enableTreeAddChild"));
    }

    private Map<String, Object> builder(Map<String, Object> treeConfig) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("treeConfig", new LinkedHashMap<>(treeConfig));
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("blockType", "AiCrudPage");
        block.put("props", props);
        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("items", new ArrayList<>(List.of(block)));
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("gridLayout", grid);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("layout", layout);
        Map<String, Object> builder = new LinkedHashMap<>();
        builder.put("pages", new LinkedHashMap<>(Map.of("page-1", page)));
        return builder;
    }

    private Map<String, Object> blockProps(Map<String, Object> builder) {
        Map<String, Object> pages = map(builder.get("pages"));
        Map<String, Object> page = map(pages.get("page-1"));
        Map<String, Object> layout = map(page.get("layout"));
        Map<String, Object> grid = map(layout.get("gridLayout"));
        List<?> items = (List<?>) grid.get("items");
        return map(map(items.get(0)).get("props"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
