package com.mdframe.forge.admin.bridge;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPageMenuDTO;
import com.mdframe.forge.plugin.system.entity.SysResource;
import com.mdframe.forge.plugin.system.mapper.SysResourceMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleResourceMapper;
import com.mdframe.forge.plugin.system.service.ISysResourceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class MenuRegisterAdapterImplTest {

    @Test
    void existingClientResourceIsUpdatedInsteadOfInserted() {
        ISysResourceService resourceService = mock(ISysResourceService.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        MenuRegisterAdapterImpl adapter = new MenuRegisterAdapterImpl(
                resourceService, resourceMapper, roleResourceMapper);

        SysResource existing = resource(100L, "pc");
        when(resourceMapper.selectOneByPermsAndClientCode(
                1L, 1, "ai:business:application:hr_apply:page:root", "pc"))
                .thenReturn(existing);
        when(resourceMapper.selectList(any())).thenReturn(List.of());

        adapter.syncApplicationPageMenus("hr_apply", List.of(menu("pc")));

        verify(resourceService, never()).save(any(SysResource.class));
        verify(resourceService).updateById(any(SysResource.class));
    }

    @Test
    void historicalResourceTypeMismatchIsUpdatedInsteadOfInserted() {
        ISysResourceService resourceService = mock(ISysResourceService.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        MenuRegisterAdapterImpl adapter = new MenuRegisterAdapterImpl(
                resourceService, resourceMapper, roleResourceMapper);

        SysResource existing = resource(101L, "pc");
        existing.setResourceType(2);
        when(resourceMapper.selectOneByPermsAndClientCode(
                1L, 1, "ai:business:application:hr_apply:page:root", "pc"))
                .thenReturn(null);
        when(resourceMapper.selectOneByPermsAndClientCodeAnyType(
                1L, "ai:business:application:hr_apply:page:root", "pc"))
                .thenReturn(existing);
        when(resourceMapper.selectList(any())).thenReturn(List.of());

        adapter.syncApplicationPageMenus("hr_apply", List.of(menu("pc")));

        verify(resourceService, never()).save(any(SysResource.class));
        verify(resourceService).updateById(any(SysResource.class));
    }

    @Test
    void samePermissionCanBeProjectedToBothClients() {
        ISysResourceService resourceService = mock(ISysResourceService.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        MenuRegisterAdapterImpl adapter = new MenuRegisterAdapterImpl(
                resourceService, resourceMapper, roleResourceMapper);

        when(resourceMapper.selectOneByPermsAndClientCode(
                1L, 1, "ai:business:application:hr_apply:page:root", "pc"))
                .thenReturn(null);
        when(resourceMapper.selectOneByPermsAndClientCode(
                1L, 1, "ai:business:application:hr_apply:page:root", "h5"))
                .thenReturn(null);
        when(resourceMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            SysResource resource = invocation.getArgument(0);
            resource.setId("h5".equals(resource.getClientCode()) ? 102L : 101L);
            return true;
        }).when(resourceService).save(any(SysResource.class));

        adapter.syncApplicationPageMenus("hr_apply", List.of(menu("pc"), menu("h5")));

        verify(resourceService, org.mockito.Mockito.times(2)).save(any(SysResource.class));
        verify(resourceMapper).selectOneByPermsAndClientCode(
                eq(1L), eq(1), eq("ai:business:application:hr_apply:page:root"), eq("pc"));
        verify(resourceMapper).selectOneByPermsAndClientCode(
                eq(1L), eq(1), eq("ai:business:application:hr_apply:page:root"), eq("h5"));
    }

    private BusinessApplicationPageMenuDTO menu(String clientCode) {
        BusinessApplicationPageMenuDTO menu = new BusinessApplicationPageMenuDTO();
        menu.setNodeId("__application_menu_root__");
        menu.setMenuName("人事申请");
        menu.setPerms("ai:business:application:hr_apply:page:root");
        menu.setSort(0);
        menu.setDirectory(true);
        menu.setVisible(true);
        menu.setClientCode(clientCode);
        return menu;
    }

    private SysResource resource(Long id, String clientCode) {
        SysResource resource = new SysResource();
        resource.setId(id);
        resource.setTenantId(1L);
        resource.setResourceType(1);
        resource.setPerms("ai:business:application:hr_apply:page:root");
        resource.setClientCode(clientCode);
        resource.setMenuStatus(0);
        resource.setVisible(0);
        return resource;
    }
}
