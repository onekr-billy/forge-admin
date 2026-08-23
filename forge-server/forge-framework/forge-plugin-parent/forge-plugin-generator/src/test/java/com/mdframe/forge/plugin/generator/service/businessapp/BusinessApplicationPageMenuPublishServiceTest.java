package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPageMenuDTO;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("an explicitly enabled page still publishes its application root and page menu")
    void explicitlyEnabledPageCreatesMenus() {
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        List<BusinessApplicationPageMenuDTO> menus = captor.getValue();
        assertEquals(List.of("__application_menu_root__", "page_apply"),
                menus.stream().map(BusinessApplicationPageMenuDTO::getNodeId).toList());
        assertEquals("/app/hr_apply", menus.get(0).getPath());
        assertEquals("/app/hr_apply?pageId=page_apply", menus.get(1).getPath());
        assertEquals("app-center/application-portal", menus.get(1).getComponent());
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
                                "systemMenuVisible", true
                        ))
                ))
        ));
        service.sync(new java.util.LinkedHashMap<>(Map.of("application", application)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        assertEquals("/app/hr-portal", captor.getValue().get(0).getPath());
        assertEquals("/app/hr-portal?pageId=page_apply", captor.getValue().get(1).getPath());
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
        BusinessApplicationPageMenuDTO page = captor.getValue().get(1);
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
                "mountTarget", "BOTH"
        ))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        assertEquals(List.of("pc", "h5", "pc", "h5"), captor.getValue().stream()
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
                                "objectRef", Map.of("configKey", "hr_apply")
                        ))
                ))
        ));
        service.sync(new java.util.LinkedHashMap<>(Map.of("application", application)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusinessApplicationPageMenuDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).syncApplicationPageMenus(eq("hr_apply"), captor.capture());
        BusinessApplicationPageMenuDTO page = captor.getValue().get(1);
        assertEquals("/pages/lowcode-runtime?configKey=hr_apply&appId=1001", page.getPath());
        assertEquals(page.getPath(), page.getComponent());
    }

    @Test
    @DisplayName("mobile pages use their own root and configured mobile parent")
    void mobilePagesUseH5RootAndRetainParentGroup() {
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
        assertEquals(List.of("h5", "h5"), menus.stream()
                .map(BusinessApplicationPageMenuDTO::getClientCode).toList());
        assertEquals(List.of("__application_menu_root__", "page_apply"), menus.stream()
                .map(BusinessApplicationPageMenuDTO::getNodeId).toList());
        assertNull(menus.get(0).getParentNodeId());
        assertEquals("84", menus.get(1).getParentNodeId());
        // H5 uses its own configured resource parent instead of a pc menu ID.
        assertEquals(true, menus.get(1).isExternalParent());
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
