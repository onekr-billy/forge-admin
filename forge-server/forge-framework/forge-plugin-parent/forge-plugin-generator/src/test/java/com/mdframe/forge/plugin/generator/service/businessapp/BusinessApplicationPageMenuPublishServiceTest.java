package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPageMenuDTO;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationPageMenuPublishService")
class BusinessApplicationPageMenuPublishServiceTest {

    @Test
    @DisplayName("pages do not create system menus unless explicitly enabled")
    void pagesDoNotCreateMenusByDefault() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表"
        ))));

        verify(adapter).syncApplicationPageMenus("hr_apply", List.of());
    }

    @Test
    @DisplayName("enabled pages without explicit parent are skipped instead of auto-mounted")
    void enabledPageWithoutParentDoesNotCreateMenus() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表",
                "systemMenuVisible", true
        ))));

        verify(adapter).syncApplicationPageMenus("hr_apply", List.of());
    }

    @Test
    @DisplayName("enabled page with explicit parent mounts only under that parent")
    void explicitlyMountedPageUsesExternalParent() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表",
                "systemMenuVisible", true,
                "menuParentId", "42"
        ))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        List<BusinessApplicationPageMenuDTO> menus = captor.getValue();
        assertEquals(1, menus.size());
        assertEquals("page_apply", menus.get(0).getNodeId());
        assertEquals("42", menus.get(0).getParentNodeId());
        assertTrue(menus.get(0).isExternalParent());
        assertEquals("/app/hr_apply?pageId=page_apply", menus.get(0).getPath());
        assertEquals("app-center/application-portal", menus.get(0).getComponent());
    }

    @Test
    @DisplayName("published menus prefer the application portal slug")
    void publishedMenusPreferPortalSlug() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        Map<String, Object> application = new java.util.LinkedHashMap<>(Map.of(
                "applicationCode", "hr_apply",
                "portalSlug", "hr-portal",
                "applicationName", "人事申请",
                "options", Map.of("inAppBuilder", Map.of(
                        "homePageId", "page_apply",
                        "nodes", List.of(Map.of(
                                "id", "page_apply",
                                "type", "page",
                                "title", "申请列表",
                                "systemMenuVisible", true,
                                "menuParentId", "42"
                        ))
                ))
        ));
        service.sync(new java.util.LinkedHashMap<>(Map.of("application", application)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        assertEquals("/app/hr-portal?pageId=page_apply", captor.getValue().get(0).getPath());
    }

    @Test
    @DisplayName("object pages publish their standalone CRUD route")
    void objectPageUsesStandaloneCrudRoute() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        Map<String, Object> application = new java.util.LinkedHashMap<>(Map.of(
                "id", "1001",
                "applicationCode", "hr_apply",
                "applicationName", "人事申请",
                "options", Map.of("inAppBuilder", Map.of(
                        "homePageId", "page_apply",
                        "nodes", List.of(Map.of(
                                "id", "page_apply",
                                "type", "page",
                                "title", "申请表单",
                                "pageType", "object",
                                "systemMenuVisible", true,
                                "menuParentId", "42",
                                "objectRef", Map.of(
                                        "configKey", "hr_apply",
                                        "pageKey", "form",
                                        "pageMode", "form",
                                        "formKey", "hr_apply_form"
                                )
                        ))
                ))
        ));
        service.sync(new java.util.LinkedHashMap<>(Map.of("application", application)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        BusinessApplicationPageMenuDTO page = captor.getValue().get(0);
        assertEquals("/ai/crud-page/hr_apply?pageKey=form&appId=1001&formKey=hr_apply_form&runtimeOpenMode=CREATE_FORM&mode=create",
                page.getPath());
        assertEquals("ai/crud-page", page.getComponent());
    }

    @Test
    @DisplayName("BOTH mount projects separate pc and h5 menu resources")
    void bothMountCreatesTwoClientMenus() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表",
                "systemMenuVisible", true,
                "mountTarget", "BOTH",
                "menuParentId", "42",
                "mobileMenuParentId", "84"
        ))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        assertEquals(List.of("pc", "h5"), captor.getValue().stream()
                .map(BusinessApplicationPageMenuDTO::getClientCode).toList());
    }

    @Test
    @DisplayName("mobile page menu points to the H5 lowcode runtime")
    void mobilePageUsesH5RuntimeTarget() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        Map<String, Object> application = new java.util.LinkedHashMap<>(Map.of(
                "id", "1001",
                "applicationCode", "hr_apply",
                "applicationName", "人事申请",
                "options", Map.of("inAppBuilder", Map.of(
                        "homePageId", "page_apply",
                        "nodes", List.of(Map.of(
                                "id", "page_apply",
                                "type", "page",
                                "title", "移动申请",
                                "systemMenuVisible", true,
                                "mountTarget", "MOBILE",
                                "mobileMenuParentId", "84",
                                "objectRef", Map.of("configKey", "hr_apply")
                        ))
                ))
        ));
        service.sync(new java.util.LinkedHashMap<>(Map.of("application", application)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        BusinessApplicationPageMenuDTO page = captor.getValue().get(0);
        assertEquals("/pages/lowcode-runtime?configKey=hr_apply&appId=1001", page.getPath());
        assertEquals(page.getPath(), page.getComponent());
    }

    @Test
    @DisplayName("mobile pages use configured mobile parent without application root")
    void mobilePagesUseConfiguredParentWithoutAppRoot() {
        MenuRegisterAdapter adapter = mock(MenuRegisterAdapter.class);
        when(adapter.syncApplicationPageMenus(eq("hr_apply"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        BusinessApplicationPageMenuPublishService service = new BusinessApplicationPageMenuPublishService(adapter);

        service.sync(snapshot(List.of(
                Map.of("id", "legacy_group", "type", "group", "title", "旧分组"),
                Map.of("id", "page_apply", "type", "page", "title", "移动申请",
                        "parentId", "legacy_group",
                        "systemMenuVisible", true, "mountTarget", "MOBILE",
                        "mobileMenuParentId", "84")
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        List<BusinessApplicationPageMenuDTO> menus = captor.getValue();
        assertEquals(1, menus.size());
        assertEquals("h5", menus.get(0).getClientCode());
        assertEquals("page_apply", menus.get(0).getNodeId());
        assertEquals("84", menus.get(0).getParentNodeId());
        assertTrue(menus.get(0).isExternalParent());
    }

    @Test
    @DisplayName("validate ignores incomplete mount flags and only checks homepage")
    void validateIgnoresIncompleteMountFlags() {
        BusinessApplicationPageMenuPublishService service =
                new BusinessApplicationPageMenuPublishService(mock(MenuRegisterAdapter.class));
        List<String> errors = service.validate(snapshot(List.of(Map.of(
                "id", "page_apply",
                "type", "page",
                "title", "申请列表",
                "systemMenuVisible", true,
                "mountTarget", "BOTH"
        ))));
        assertTrue(errors.stream().noneMatch(error -> error.contains("父级菜单")));
    }

    private Map<String, Object> snapshot(List<Map<String, Object>> nodes) {
        return new java.util.LinkedHashMap<>(Map.of(
                "application", new java.util.LinkedHashMap<>(Map.of(
                        "applicationCode", "hr_apply",
                        "applicationName", "人事申请",
                        "options", Map.of("inAppBuilder", Map.of(
                                "homePageId", "page_apply",
                                "nodes", nodes
                        ))
                ))
        ));
    }
}
