package com.mdframe.forge.plugin.generator.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessDocumentConfigService;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.plugin.generator.service.formula.VirtualFormulaRuntime;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePolicyService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeSchemaValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.MySqlRuntimeDatabaseDialect;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeJdbcTemplateProvider;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real draft compiler and SQL repository; only the database/session boundaries are mocked. */
class DynamicCrudServiceChildListTest {

    private static final String CHILD_FIELD = "test_detail__itemName";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AiCrudConfigVersionMapper versions = mock(AiCrudConfigVersionMapper.class);
    private final List<String> dataSql = new ArrayList<>();
    private AiCrudConfigService configs;
    private DynamicCrudService service;
    private AiCrudConfig draft;

    @BeforeEach
    void setUp() {
        draft = draftConfig();
        configs = spy(new AiCrudConfigService(objectMapper, null,
                new LowcodeRuntimeConfigBuilder(objectMapper, new LowcodeSchemaValidator(), new LowcodePolicyService()),
                mock(BusinessDocumentConfigService.class), versions, mock(AiPageTemplateService.class)));
        doReturn(draft).when(configs).getByConfigKey("test_order");
        doReturn(true).when(configs).hasDesignPreviewPermission();

        RuntimeJdbcTemplateProvider provider = mock(RuntimeJdbcTemplateProvider.class);
        when(provider.namedJdbcTemplate(any())).thenReturn(jdbc);
        DynamicCrudRepository repository = spy(new DynamicCrudRepository(jdbc, provider,
                new RuntimeDatabaseDialectFactory(List.of(new MySqlRuntimeDatabaseDialect()))));
        doReturn(true).when(repository).tableExists(anyString());
        doReturn(Set.of("id", "title", "tenant_id", "del_flag"))
                .when(repository).getTableColumns("test_order");
        doReturn(Set.of("id", "order_id", "item_name", "tenant_id", "del_flag"))
                .when(repository).getTableColumns("test_detail");

        LowcodeRuntimeDataSourceResolver resolver = mock(LowcodeRuntimeDataSourceResolver.class);
        when(resolver.resolve(any(AiCrudConfig.class))).thenReturn(LowcodeRuntimeDataSourceContext.master("test_order"));
        service = new DynamicCrudService(repository, configs, objectMapper, null, null, null,
                new LowcodeEncryptConfigParser(objectMapper), mock(DynamicDataScopeService.class),
                null, null, null, null, mock(VirtualFormulaRuntime.class), resolver, null);
        TenantContextHolder.setTenantId(1L);
        previewRequest();
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        TenantContextHolder.clear();
    }

    @Test
    void previewCompilesSelectedChildFieldsForBothHeadersAndAggregateQuery() {
        stubRows(2L, List.of(row(1L, "甲、乙"), row(2L, null)));
        String originalColumns = draft.getColumnsSchema();
        String originalPage = draft.getPageSchema();
        JsonNode headers = objectMapper.valueToTree(configs.buildDraftRenderConfig(draft).getColumnsSchema());
        assertTrue(headers.toString().contains(CHILD_FIELD));

        Page<Map<String, Object>> page = service.selectPage("test_order", new PageQuery(), null);

        String sql = dataSql.get(0);
        assertTrue(sql.contains("AS `" + CHILD_FIELD + "`"), sql);
        assertTrue(sql.contains("GROUP_CONCAT(`item_name` ORDER BY `id` SEPARATOR '、')"), sql);
        assertTrue(sql.contains("t1.order_id = t0.id"), sql);
        assertTrue(sql.contains("`tenant_id` = :tenantId"), sql);
        assertTrue(sql.contains("`del_flag` = :logicActiveValue"), sql);
        assertEquals(2L, page.getTotal());
        assertEquals("甲、乙", page.getRecords().get(0).get(CHILD_FIELD));
        assertTrue(page.getRecords().get(1).containsKey(CHILD_FIELD));
        assertNull(page.getRecords().get(1).get(CHILD_FIELD));
        assertFalse(page.getRecords().get(0).containsKey("__listRowKey"));
        assertEquals(originalColumns, draft.getColumnsSchema());
        assertEquals(originalPage, draft.getPageSchema());
        verify(configs, never()).updateById(any(AiCrudConfig.class));
        verifyNoInteractions(versions);
    }

    @Test
    void previewKeepsExpandedValuesAndStableRowKeys() {
        draft.setPageSchema(draft.getPageSchema().replace("aggregate", "expand"));
        Map<String, Object> first = row(1L, "甲");
        first.put("__childId_t1", 11L);
        Map<String, Object> second = row(1L, "乙");
        second.put("__childId_t1", 12L);
        Map<String, Object> empty = row(2L, null);
        empty.put("__childId_t1", null);
        stubRows(3L, List.of(first, second, empty));

        Page<Map<String, Object>> page = service.selectPage("test_order", new PageQuery(), null);

        String sql = dataSql.get(0);
        assertTrue(sql.contains("AS `" + CHILD_FIELD + "`"), sql);
        assertTrue(sql.contains("LEFT JOIN test_detail t1 ON"), sql);
        assertFalse(sql.contains("GROUP_CONCAT"), sql);
        assertEquals(3L, page.getTotal());
        assertEquals(List.of("1:11", "1:12", "2:"),
                page.getRecords().stream().map(row -> row.get("__listRowKey")).toList());
        assertEquals("甲", page.getRecords().get(0).get(CHILD_FIELD));
        assertEquals("乙", page.getRecords().get(1).get(CHILD_FIELD));
        assertFalse(page.getRecords().get(0).containsKey("__childId_t1"));
    }

    @Test
    void regularRequestUsesPublishedColumnsEvenWhenDraftHasNewChildColumns() {
        RequestContextHolder.resetRequestAttributes();
        stubPublishedVersion();
        stubRows(1L, List.of(new LinkedHashMap<>(Map.of("id", 1L, "title", "示例"))));

        Page<Map<String, Object>> page = service.selectPage("test_order", new PageQuery(), null);

        assertFalse(dataSql.get(0).contains("test_detail"));
        assertFalse(page.getRecords().get(0).containsKey(CHILD_FIELD));
        verify(versions).selectVersionByNo(1L, 10L, 1);
    }

    @Test
    void previewParameterWithoutDesignPermissionCannotReadDraftColumns() {
        doReturn(false).when(configs).hasDesignPreviewPermission();
        stubPublishedVersion();
        stubRows(1L, List.of(new LinkedHashMap<>(Map.of("id", 1L, "title", "示例"))));

        service.selectPage("test_order", new PageQuery(), null);

        assertFalse(dataSql.get(0).contains("test_detail"));
        verify(versions).selectVersionByNo(1L, 10L, 1);
    }

    @Test
    void unpublishedConfigWithoutDesignPermissionIsRejected() {
        doReturn(false).when(configs).hasDesignPreviewPermission();
        draft.setPublishStatus("DRAFT");

        assertThrows(BusinessException.class, () -> service.selectPage("test_order", new PageQuery(), null));
        verifyNoInteractions(jdbc);
    }

    private void previewRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("designPreview", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void stubRows(long total, List<Map<String, Object>> rows) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(total);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            dataSql.add(invocation.getArgument(0));
            MapSqlParameterSource params = invocation.getArgument(1);
            assertEquals(1L, params.getValue("tenantId"));
            return rows;
        });
    }

    private Map<String, Object> row(long id, String childValue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", "示例");
        row.put(CHILD_FIELD, childValue);
        return row;
    }

    private void stubPublishedVersion() {
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setModelSchema(draft.getModelSchema());
        version.setPageSchema(draft.getPageSchema());
        version.setColumnsSchema(draft.getColumnsSchema());
        version.setSearchSchema("[]");
        version.setEditSchema("[]");
        version.setApiConfig("{}");
        version.setOptions("{}");
        when(versions.selectVersionByNo(1L, 10L, 1)).thenReturn(version);
    }

    private AiCrudConfig draftConfig() {
        AiCrudConfig config = new AiCrudConfig();
        config.setId(10L);
        config.setTenantId(1L);
        config.setConfigKey("test_order");
        config.setObjectCode("test_order");
        config.setTableName("test_order");
        config.setStatus("0");
        config.setMode("CONFIG");
        config.setBuildMode("LOWCODE");
        config.setPublishStatus("PUBLISHED");
        config.setPublishedVersion(1);
        config.setLayoutType("master-detail-crud");
        config.setColumnsSchema("[{\"key\":\"title\",\"dataIndex\":\"title\",\"title\":\"名称\"}]");
        config.setSearchSchema("[]");
        config.setEditSchema("[]");
        config.setApiConfig("{}");
        config.setOptions("{}");
        config.setModelSchema("""
                {"appType":"SINGLE","tableMode":"EXISTING","tableName":"test_order","businessName":"示例单据",
                 "fields":[{"field":"id","columnName":"id","dataType":"bigint","label":"ID","primaryKey":true,
                            "autoIncrement":true,"systemField":true,"readonly":true,"formVisible":false},
                           {"field":"title","columnName":"title","dataType":"varchar","label":"名称",
                            "componentType":"input","listVisible":true,"formVisible":true}],
                 "relations":[{"relationType":"ONE_TO_MANY","targetObjectCode":"test_detail",
                               "sourceField":"id","targetField":"orderId"}]}
                """);
        config.setPageSchema("""
                {"layoutType":"master-detail-crud","primaryModelCode":"test_order",
                 "modelRefs":[{"modelCode":"test_order","tableName":"test_order","primary":true},
                              {"modelCode":"test_detail","tableName":"test_detail","modelName":"明细","primary":false,
                               "fields":[{"field":"id","columnName":"id","label":"ID"},
                                         {"field":"orderId","columnName":"order_id","label":"所属单据"},
                                         {"field":"itemName","sourceField":"itemName","fieldRef":"test_detail__itemName",
                                          "columnName":"item_name","label":"明细名称","listVisible":false,
                                          "formVisible":true,"dataType":"varchar","componentType":"input"}]}],
                 "zones":[{"zoneKey":"table","enabled":true,"fieldRefs":["title"],
                           "props":{"childListDisplayMode":"aggregate"}}],
                 "listGridLayout":{"items":[{"blockType":"AiCrudPage","fieldRefs":["title","test_detail__itemName"],
                                               "props":{"childListDisplayMode":"aggregate"}}]}}
                """);
        return config;
    }
}
