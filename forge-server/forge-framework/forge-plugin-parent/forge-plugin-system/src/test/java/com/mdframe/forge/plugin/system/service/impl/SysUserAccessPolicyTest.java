package com.mdframe.forge.plugin.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.system.constant.SystemConstants;
import com.mdframe.forge.plugin.system.mapper.SysTenantMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SysUserAccessPolicyTest {

    private final SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
    private final SysUserAccessPolicy policy = new SysUserAccessPolicy(
        mock(SysUserMapper.class), mock(SysUserTenantMapper.class), tenantMapper,
        mock(SysUserAssignmentPolicy.class));

    @Test
    void acceptsScopedWildcardPermissionForOrdinaryOperator() {
        LoginUser loginUser = loginUser(SystemConstants.UserType.NORMAL_USER, 7L, 10L,
            Set.of("system:user:*"));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);

            assertDoesNotThrow(policy::assertUserManagementAllowed);
        }
    }

    @Test
    void rejectsOrdinaryOperatorWithoutManagementPermission() {
        LoginUser loginUser = loginUser(SystemConstants.UserType.NORMAL_USER, 7L, 10L,
            Set.of("system:notice:list"));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);

            RuntimeException error = assertThrows(RuntimeException.class,
                policy::assertUserManagementAllowed);
            assertEquals("无权访问用户组织管理功能", error.getMessage());
        }
    }

    @Test
    void administratorTenantSelectionIsDeduplicatedAndValidated() {
        LoginUser loginUser = loginUser(SystemConstants.UserType.SYSTEM_ADMIN, 1L, 10L, Set.of());
        when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        List<Long> tenantIds = policy.resolveWriteTenantIds(List.of(20L, 20L, 10L), 10L, loginUser);

        assertEquals(List.of(20L, 10L), tenantIds);
    }

    private LoginUser loginUser(Integer userType, Long userId, Long tenantId, Set<String> permissions) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType(userType);
        loginUser.setUserId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setPermissions(permissions);
        return loginUser;
    }
}
