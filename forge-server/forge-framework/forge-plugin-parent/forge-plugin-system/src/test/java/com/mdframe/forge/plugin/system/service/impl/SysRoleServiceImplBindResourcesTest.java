package com.mdframe.forge.plugin.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mdframe.forge.plugin.system.entity.SysResource;
import com.mdframe.forge.plugin.system.entity.SysRole;
import com.mdframe.forge.plugin.system.entity.SysRoleResource;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysResourceMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleResourceMapper;
import com.mdframe.forge.plugin.system.mapper.SysTenantMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import com.mdframe.forge.plugin.system.service.ISysResourceService;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.datascope.mapper.SysDataScopeConfigMapper;
import com.mdframe.forge.starter.datascope.mapper.SysRoleModuleDataScopeMapper;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色资源全量保存的归一化契约：
 * 目录（resourceType=1）只有被选中的非目录后代支撑时才允许写入，
 * 防止授权弹窗回填的历史"孤儿目录"被反复写回，形成点开为空的菜单入口。
 */
class SysRoleServiceImplBindResourcesTest {

    private static final Long ROLE_ID = 30L;
    private static final Long TENANT_ID = 1L;

    /** 1(目录) -> 100(目录) -> 101(页面)；778 为挂在 200 页面下的 API；9001 为孤儿目录；777 为独立 API */
    private static final Map<Long, SysResource> RESOURCES = Map.of(
            1L, resource(1L, 1, 0L),
            100L, resource(100L, 1, 1L),
            101L, resource(101L, 2, 100L),
            200L, resource(200L, 2, 100L),
            778L, resource(778L, 4, 200L),
            777L, resource(777L, 4, 0L),
            9001L, resource(9001L, 1, 0L));

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                SysRoleResource.class);
    }

    @Test
    void bindShouldDropOrphanDirectoriesAndKeepAncestorsOfSelectedPages() {
        Dependencies dependencies = new Dependencies();
        SysRoleServiceImpl service = dependencies.service();
        when(dependencies.roleMapper.selectById(ROLE_ID)).thenReturn(role());
        when(dependencies.resourceService.listByIds(anyCollection())).thenAnswer(invocation ->
                lookup(invocation.getArgument(0)));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(admin());
            assertThat(service.bindRoleResources(ROLE_ID, new Long[]{101L, 9001L})).isTrue();
        }

        verify(dependencies.roleResourceMapper).delete(any(Wrapper.class));
        ArgumentCaptor<List<SysRoleResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(dependencies.roleResourceMapper).insertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(SysRoleResource::getResourceId)
                .containsExactlyInAnyOrder(101L, 100L, 1L);
    }

    @Test
    void bindShouldClearAllBindingsWhenOnlyOrphanDirectoriesSubmitted() {
        Dependencies dependencies = new Dependencies();
        SysRoleServiceImpl service = dependencies.service();
        when(dependencies.roleMapper.selectById(ROLE_ID)).thenReturn(role());
        when(dependencies.resourceService.listByIds(anyCollection())).thenAnswer(invocation ->
                lookup(invocation.getArgument(0)));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(admin());
            assertThat(service.bindRoleResources(ROLE_ID, new Long[]{9001L})).isTrue();
        }

        verify(dependencies.roleResourceMapper).delete(any(Wrapper.class));
        verify(dependencies.roleResourceMapper, never()).insertBatch(any());
    }

    @Test
    void bindShouldKeepNonDirectoryResourcesWithoutDirectoryAncestors() {
        Dependencies dependencies = new Dependencies();
        SysRoleServiceImpl service = dependencies.service();
        when(dependencies.roleMapper.selectById(ROLE_ID)).thenReturn(role());
        when(dependencies.resourceService.listByIds(anyCollection())).thenAnswer(invocation ->
                lookup(invocation.getArgument(0)));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(admin());
            assertThat(service.bindRoleResources(ROLE_ID, new Long[]{777L})).isTrue();
        }

        ArgumentCaptor<List<SysRoleResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(dependencies.roleResourceMapper).insertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(SysRoleResource::getResourceId)
                .containsExactly(777L);
    }

    @Test
    void bindShouldKeepApiPermissionWithoutItsPageOrDirectoryAncestors() {
        Dependencies dependencies = new Dependencies();
        SysRoleServiceImpl service = dependencies.service();
        when(dependencies.roleMapper.selectById(ROLE_ID)).thenReturn(role());
        when(dependencies.resourceService.listByIds(anyCollection())).thenAnswer(invocation ->
                lookup(invocation.getArgument(0)));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(admin());
            assertThat(service.bindRoleResources(ROLE_ID, new Long[]{778L})).isTrue();
        }

        ArgumentCaptor<List<SysRoleResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(dependencies.roleResourceMapper).insertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(SysRoleResource::getResourceId)
                .containsExactly(778L);
    }

    private static List<SysResource> lookup(Collection<Long> ids) {
        return ids.stream()
                .map(RESOURCES::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static SysResource resource(Long id, Integer resourceType, Long parentId) {
        SysResource resource = new SysResource();
        resource.setId(id);
        resource.setResourceType(resourceType);
        resource.setParentId(parentId);
        resource.setClientCode("pc");
        return resource;
    }

    private static SysRole role() {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT_ID);
        role.setDataScope(5);
        return role;
    }

    private static LoginUser admin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setTenantId(TENANT_ID);
        loginUser.setUserType(0);
        return loginUser;
    }

    private static final class Dependencies {
        private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        private final SysRoleOrgMapper roleOrgMapper = mock(SysRoleOrgMapper.class);
        private final SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        private final SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        private final SysUserOrgRoleMapper userOrgRoleMapper = mock(SysUserOrgRoleMapper.class);
        private final SysOrgMapper orgMapper = mock(SysOrgMapper.class);
        private final SysUserMapper userMapper = mock(SysUserMapper.class);
        private final SysUserTenantMapper userTenantMapper = mock(SysUserTenantMapper.class);
        private final SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        private final SysDataScopeConfigMapper dataScopeConfigMapper = mock(SysDataScopeConfigMapper.class);
        private final SysRoleModuleDataScopeMapper roleModuleDataScopeMapper =
                mock(SysRoleModuleDataScopeMapper.class);
        private final IDataScopeService dataScopeService = mock(IDataScopeService.class);
        private final ISysResourceService resourceService = mock(ISysResourceService.class);

        private SysRoleServiceImpl service() {
            return new SysRoleServiceImpl(
                    roleMapper,
                    roleOrgMapper,
                    roleResourceMapper,
                    tenantMapper,
                    userRoleMapper,
                    userOrgRoleMapper,
                    orgMapper,
                    userMapper,
                    userTenantMapper,
                    resourceMapper,
                    dataScopeConfigMapper,
                    roleModuleDataScopeMapper,
                    dataScopeService,
                    resourceService);
        }
    }
}
