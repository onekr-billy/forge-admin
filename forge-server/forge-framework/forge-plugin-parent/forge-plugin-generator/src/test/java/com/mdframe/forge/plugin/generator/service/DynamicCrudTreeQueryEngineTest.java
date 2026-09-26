package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCrudTreeQueryEngineTest {

    private final DynamicCrudRepository repository = mock(DynamicCrudRepository.class);
    private final AiCrudConfigService configService = mock(AiCrudConfigService.class);
    private DynamicCrudTreeQueryEngine engine;
    private AiCrudConfig config;

    @BeforeEach
    void setUp() {
        engine = new DynamicCrudTreeQueryEngine(repository, configService, new ObjectMapper());
        config = new AiCrudConfig();
        config.setConfigKey("org-tree");
        config.setTableName("org_node");
        config.setLayoutType("tree-crud");
        config.setModelSchema("""
                {"treeConfig":{"sourceTableName":"org_node","keyField":"id","parentField":"parentId","labelField":"name",
                "filterField":"orgId","targetField":"id","childrenField":"items","loadMode":"full"}}
                """);
    }

    @Test
    void optionsOverrideModelConfigAndDefaultsRemainStable() {
        config.setOptions("""
                {"treeConfig":{"labelField":"title","loadMode":"lazy","lazy":true}}
                """);

        LowcodeTreeConfig tree = engine.resolveTreeConfig(config);

        assertThat(tree.getKeyField()).isEqualTo("id");
        assertThat(tree.getParentField()).isEqualTo("parentId");
        assertThat(tree.getLabelField()).isEqualTo("title");
        assertThat(tree.getFilterField()).isEqualTo("orgId");
        assertThat(tree.getChildrenField()).isEqualTo("items");
        assertThat(tree.getLoadMode()).isEqualTo("lazy");
        assertThat(engine.isTreeRuntime(config)).isTrue();
    }

    @Test
    void fullTreeRestoresAncestorsAndMarksThemReadOnlyBeforeReadPipeline() {
        LowcodeTreeConfig tree = engine.resolveTreeConfig(config);
        Map<String, String> columns = Map.of("id", "id", "parentId", "parent_id", "name", "name");
        DynamicCrudRepository.SqlCondition scope = new DynamicCrudRepository.SqlCondition(
                "create_by = :owner", Map.of("owner", 9L));
        when(repository.getTableColumns("org_node")).thenReturn(Set.of("id", "parent_id", "name"));
        when(repository.selectList(eq("org_node"), eq(null), anySet(), anyMap(), eq(columns),
                eq("id ASC"), eq(5000), eq(scope)))
                .thenReturn(List.of(row(2L, 1L, "child")));
        when(repository.selectListByColumn("org_node", "id", 1L))
                .thenReturn(List.of(row(1L, 0L, "root")));

        List<Map<String, Object>> result = engine.selectTree(
                config, tree, null, null, null, null, null, columns, scope,
                rows -> rows.forEach(row -> row.put("pipelineApplied", true)));

        assertThat(result).hasSize(1);
        Map<String, Object> root = result.get(0);
        assertThat(root)
                .containsEntry("id", 1L)
                .containsEntry("label", "root")
                .containsEntry("_scopeAncestor", true)
                .containsEntry("_dataScopeWritable", false)
                .containsEntry("pipelineApplied", true)
                .containsEntry("isLeaf", false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) root.get("items");
        assertThat(children).singleElement().satisfies(child -> assertThat(child)
                .containsEntry("id", 2L)
                .containsEntry("label", "child")
                .containsEntry("pipelineApplied", true)
                .containsEntry("isLeaf", true)
                .doesNotContainKey("items"));
    }

    @Test
    void lazyTreeUsesScopedChildProbeAndKeepsNodeProtocol() {
        LowcodeTreeConfig tree = engine.resolveTreeConfig(config);
        Map<String, String> columns = Map.of("id", "id", "parentId", "parent_id", "name", "name");
        DynamicCrudRepository.SqlCondition scope = new DynamicCrudRepository.SqlCondition("tenant_id = 1", Map.of());
        when(repository.getTableColumns("org_node")).thenReturn(Set.of("id", "parent_id", "name"));
        when(repository.selectTreeChildren("org_node", "parent_id", "7", "name DESC", 5000, scope))
                .thenReturn(List.of(row(8L, 7L, "leaf")));
        when(repository.existsByColumn("org_node", "parent_id", 8L, scope)).thenReturn(false);

        List<Map<String, Object>> result = engine.selectTree(
                config, tree, "7", null, "lazy", "name", "desc", columns, scope, rows -> { });

        assertThat(result).singleElement().satisfies(node -> assertThat(node)
                .containsEntry("key", 8L)
                .containsEntry("label", "leaf")
                .containsEntry("targetValue", 8L)
                .containsEntry("isLeaf", true));
        verify(repository).existsByColumn("org_node", "parent_id", 8L, scope);
    }

    @Test
    void includeChildrenExpandsDescendantsAndRemovesProtocolFlag() {
        when(repository.tableExists("org_node")).thenReturn(true);
        when(repository.getColumnMapping("org_node"))
                .thenReturn(Map.of("id", "id", "parentId", "parent_id"));
        when(repository.selectListByColumn("org_node", "parent_id", 1L))
                .thenReturn(List.of(row(2L, 1L, "child")));
        when(repository.selectListByColumn("org_node", "parent_id", 2L))
                .thenReturn(List.of(row(3L, 2L, "grandchild")));
        when(repository.selectListByColumn("org_node", "parent_id", 3L)).thenReturn(List.of());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orgId", 1L);
        params.put("orgId_includeChildren", true);
        Set<String> allowed = new LinkedHashSet<>();
        Map<String, String> searchTypes = new LinkedHashMap<>();

        Map<String, Object> expanded = engine.expandIncludeChildrenParams(
                params, config, "business_record", allowed, searchTypes);

        assertThat(expanded).containsEntry("orgId", List.of(1L, 2L, 3L))
                .doesNotContainKey("orgId_includeChildren");
        assertThat(allowed).containsExactly("orgId");
        assertThat(searchTypes).containsEntry("orgId", "in");
    }

    @Test
    void preExpandedCustomConditionUsesInWithoutAnotherTreeQuery() {
        CustomQueryConditionDTO condition = new CustomQueryConditionDTO();
        condition.setField("orgId");
        condition.setOperator("eq");
        condition.setValue("1,2,3");
        condition.setIncludeChildren(true);

        List<CustomQueryConditionDTO> expanded = engine.expandCustomIncludeChildrenConditions(
                List.of(condition), config, "business_record", Set.of("orgId"));

        assertThat(expanded).singleElement().satisfies(next -> {
            assertThat(next.getField()).isEqualTo("orgId");
            assertThat(next.getOperator()).isEqualTo("in");
            assertThat(next.getValue()).isEqualTo(List.of("1", "2", "3"));
            assertThat(next.getIncludeChildren()).isTrue();
        });
    }

    private Map<String, Object> row(Long id, Long parentId, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("parent_id", parentId);
        row.put("name", name);
        return row;
    }
}
