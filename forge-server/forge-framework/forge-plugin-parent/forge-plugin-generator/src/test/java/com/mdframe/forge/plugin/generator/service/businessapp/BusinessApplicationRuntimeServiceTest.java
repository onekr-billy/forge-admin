package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessApplicationRuntimeService")
class BusinessApplicationRuntimeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("normal runtime reads the immutable published version")
    void readsPublishedVersionSnapshot() throws Exception {
        BusinessApplicationVO application = application(3);
        Map<String, Object> builder = Map.of(
                "homePageId", "page_home",
                "nodes", List.of(node("page_home", null, null)),
                "pages", Map.of("page_home", Map.of("title", "已发布首页")));
        Map<String, Object> snapshot = Map.of(
                "application", Map.of(
                        "id", "10",
                        "applicationCode", "crm_test",
                        "applicationName", "发布时名称",
                        "suiteCode", "crm",
                        "status", 1,
                        "options", Map.of("inAppBuilder", builder)),
                "objects", List.of(Map.of(
                        "objectId", "11", "objectCode", "customer", "objectName", "客户",
                        "objectRole", "PRIMARY")),
                "entries", List.of());
        AiBusinessApplicationVersion version = version(3, objectMapper.writeValueAsString(snapshot));
        BusinessApplicationRuntimeService service = service(application, version);

        BusinessApplicationRuntimeVO runtime = service.runtimeByCode("crm_test");

        assertEquals(3, runtime.getVersionNo());
        assertEquals("发布时名称", runtime.getApplication().getApplicationName());
        assertEquals(3, runtime.getApplication().getLastPublishVersion());
        assertEquals(11L, runtime.getObjects().get(0).getObjectId());
        assertTrue(runtime.getApplication().getOptions().contains("已发布首页"));
    }

    @Test
    @DisplayName("unpublished application has no formal runtime configuration")
    void unpublishedApplicationIsRejected() {
        BusinessApplicationRuntimeService service = service(application(null), null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.runtimeByCode("crm_test"));

        assertTrue(error.getMessage().contains("尚未发布"));
    }

    @Test
    @DisplayName("RBAC page filtering keeps internal pages and falls back to the first accessible page")
    void filtersPagesAndFallsBackHome() {
        BusinessApplicationRuntimeService service = service(application(1), version(1, "{}"));
        Map<String, Object> builder = new LinkedHashMap<>();
        builder.put("homePageId", "page_admin");
        builder.put("nodes", List.of(
                group("group_sales"),
                menuNode("page_admin", null),
                menuNode("page_sales", "group_sales"),
                hiddenNode("page_hidden")));
        builder.put("pages", Map.of(
                "page_admin", Map.of("title", "管理页"),
                "page_sales", Map.of("title", "销售页"),
                "page_hidden", Map.of("title", "隐藏页")));

        Map<String, Object> filtered = service.filterBuilder(builder, "crm_test",
                Set.of("ai:business:application:crm_test:page:page_sales",
                        "ai:business:application:crm_test:page:group_sales"));
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) filtered.get("nodes");
        Map<String, Object> pages = (Map<String, Object>) filtered.get("pages");

        assertEquals("page_sales", filtered.get("homePageId"));
        assertEquals(List.of("group_sales", "page_sales", "page_hidden"),
                nodes.stream().map(item -> String.valueOf(item.get("id"))).toList());
        assertEquals(Set.of("page_sales", "page_hidden"), pages.keySet());
        assertFalse(pages.containsKey("page_admin"));
    }

    @Test
    @DisplayName("administrator bypasses page role restrictions")
    void administratorBypassesPageRoleRestrictions() {
        BusinessApplicationRuntimeService service = service(application(1), version(1, "{}"));
        Map<String, Object> builder = new LinkedHashMap<>();
        builder.put("homePageId", "page_admin");
        builder.put("nodes", List.of(
                menuNode("page_admin", null),
                menuNode("page_sales", null)));
        builder.put("pages", Map.of(
                "page_admin", Map.of("title", "管理页"),
                "page_sales", Map.of("title", "销售页")));

        Map<String, Object> filtered = service.filterBuilder(builder, "crm_test", Set.of(), true);

        assertEquals("page_admin", filtered.get("homePageId"));
        assertEquals(Set.of("page_admin", "page_sales"),
                ((Map<?, ?>) filtered.get("pages")).keySet());
    }

    @Test
    @DisplayName("RBAC filtering supports legacy system-menu markers stored in settings")
    void filtersLegacySettingsSystemMenuPage() {
        BusinessApplicationRuntimeService service = service(application(1), version(1, "{}"));
        Map<String, Object> builder = new LinkedHashMap<>();
        builder.put("homePageId", "page_legacy");
        builder.put("nodes", List.of(settingsMenuNode("page_legacy")));
        builder.put("pages", Map.of("page_legacy", Map.of("title", "历史菜单页")));

        Map<String, Object> filtered = service.filterBuilder(builder, "crm_test", Set.of());

        assertEquals(List.of(), filtered.get("nodes"));
        assertEquals(Map.of(), filtered.get("pages"));
        assertEquals(null, filtered.get("homePageId"));
    }

    private BusinessApplicationRuntimeService service(BusinessApplicationVO application,
                                                      AiBusinessApplicationVersion version) {
        BusinessApplicationSnapshotService snapshotService = new BusinessApplicationSnapshotService(
                objectMapper, null, null, null, null, null, null, null, null);
        return new BusinessApplicationRuntimeService(
                new StubApplicationService(application),
                new StubVersionService(version),
                snapshotService,
                objectMapper);
    }

    private BusinessApplicationVO application(Integer lastPublishVersion) {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(10L);
        application.setApplicationCode("crm_test");
        application.setApplicationName("草稿名称");
        application.setSuiteName("CRM");
        application.setStatus(1);
        application.setLastPublishVersion(lastPublishVersion);
        return application;
    }

    private AiBusinessApplicationVersion version(Integer versionNo, String snapshotJson) {
        if (versionNo == null) {
            return null;
        }
        AiBusinessApplicationVersion version = new AiBusinessApplicationVersion();
        version.setApplicationId(10L);
        version.setVersionNo(versionNo);
        version.setSnapshotJson(snapshotJson);
        return version;
    }

    private Map<String, Object> group(String id) {
        return Map.of("id", id, "type", "group", "title", id, "sort", 0,
                "systemMenuVisible", true);
    }

    private Map<String, Object> menuNode(String id, String parentId) {
        Map<String, Object> node = node(id, parentId, null);
        node.put("systemMenuVisible", true);
        return node;
    }

    private Map<String, Object> settingsMenuNode(String id) {
        Map<String, Object> node = node(id, null, null);
        node.put("settings", Map.of("systemMenuVisible", true));
        return node;
    }

    private Map<String, Object> node(String id, String parentId, Map<String, Object> access) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "page");
        node.put("title", id);
        node.put("sort", "page_admin".equals(id) ? 0 : 10);
        if (parentId != null) {
            node.put("parentId", parentId);
        }
        if (access != null) {
            node.put("access", access);
        }
        return node;
    }

    private Map<String, Object> hiddenNode(String id) {
        Map<String, Object> node = new LinkedHashMap<>(node(id, null, null));
        node.put("navigationVisible", false);
        return node;
    }

    private static class StubApplicationService extends BusinessApplicationService {

        private final BusinessApplicationVO application;

        StubApplicationService(BusinessApplicationVO application) {
            super(null, null, null, null);
            this.application = application;
        }

        @Override
        public BusinessApplicationVO detailByCode(String applicationCode) {
            return application;
        }
    }

    private static class StubVersionService extends BusinessApplicationVersionService {

        private final AiBusinessApplicationVersion version;

        StubVersionService(AiBusinessApplicationVersion version) {
            super(null, null, null);
            this.version = version;
        }

        @Override
        public AiBusinessApplicationVersion requireVersion(Long applicationId, Integer versionNo) {
            return version;
        }
    }
}
