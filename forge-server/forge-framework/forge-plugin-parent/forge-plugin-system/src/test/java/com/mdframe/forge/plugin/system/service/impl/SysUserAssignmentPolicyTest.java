package com.mdframe.forge.plugin.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.system.constant.SystemConstants;
import com.mdframe.forge.plugin.system.entity.SysRole;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.entity.SysUserTenant;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysPostMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserAssignmentPolicyTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserTenantMapper userTenantMapper = mock(SysUserTenantMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRoleOrgMapper roleOrgMapper = mock(SysRoleOrgMapper.class);
    private final SysUserAssignmentPolicy policy = new SysUserAssignmentPolicy(
        userMapper, userTenantMapper, roleMapper, roleOrgMapper,
        mock(SysUserOrgMapper.class), mock(SysOrgMapper.class), mock(SysPostMapper.class));

    @Test
    void systemAdministratorKeepsGlobalUserTypeWithoutTenantMembershipLookup() {
        SysUser user = new SysUser();
        user.setUserType(SystemConstants.UserType.SYSTEM_ADMIN);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertEquals(SystemConstants.UserType.SYSTEM_ADMIN,
            policy.resolveEffectiveUserType(1L, 10L));
        verify(userTenantMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void rejectsTenantWideRoleForNormalUserWithActionableMessage() {
        SysUser user = new SysUser();
        user.setUserType(SystemConstants.UserType.NORMAL_USER);
        when(userMapper.selectById(2L)).thenReturn(user);
        SysUserTenant member = new SysUserTenant();
        member.setMemberType(SystemConstants.UserType.NORMAL_USER);
        when(userTenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
        SysRole role = new SysRole();
        role.setId(20L);
        role.setRoleName("采购主管");
        role.setDataScope(SystemConstants.RoleDataScope.TENANT);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> policy.validateRoleDataScopeForTarget(List.of(20L), 2L, 10L));

        assertTrue(error.getMessage().contains("采购主管"));
        assertTrue(error.getMessage().contains("租户管理员"));
    }

    @Test
    void normalizesUnknownUserTypesToNormalUser() {
        assertEquals(SystemConstants.UserType.NORMAL_USER, policy.normalizeUserType(null));
        assertEquals(SystemConstants.UserType.NORMAL_USER, policy.normalizeUserType(99));
        assertEquals(SystemConstants.UserType.TENANT_ADMIN,
            policy.normalizeUserType(SystemConstants.UserType.TENANT_ADMIN));
    }
}
