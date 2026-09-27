package com.mdframe.forge.plugin.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysPostMapper;
import com.mdframe.forge.plugin.system.mapper.SysRegionMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysTenantMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserPostMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.plugin.system.service.PasswordPolicyService;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceImplPasswordResetTest {

    @Test
    void administratorResetShouldAdvanceVersionAndKickOutEverySession() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser targetUser = new SysUser();
        targetUser.setId(11L);
        targetUser.setTenantId(1L);
        targetUser.setPassword("encoded-old-password");
        when(userMapper.selectById(11L)).thenReturn(targetUser);
        when(userMapper.resetUserPassword(any(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        SysUserServiceImpl service = new SysUserServiceImpl(
                userMapper,
                mock(SysUserRoleMapper.class),
                mock(SysUserOrgRoleMapper.class),
                mock(SysUserOrgMapper.class),
                mock(SysUserPostMapper.class),
                mock(SysUserTenantMapper.class),
                mock(SysTenantMapper.class),
                mock(SysRoleMapper.class),
                mock(SysRoleOrgMapper.class),
                mock(SysOrgMapper.class),
                mock(SysPostMapper.class),
                mock(SysRegionMapper.class),
                mock(IUserLoadService.class),
                passwordPolicyService);
        LoginUser administrator = new LoginUser();
        administrator.setUserId(1L);
        administrator.setUserType(0);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(administrator);

            assertThat(service.resetPassword(11L, "NewPass123")).isTrue();

            verify(userMapper).resetUserPassword(
                    org.mockito.ArgumentMatchers.eq(11L), anyString(), any(LocalDateTime.class));
            verify(passwordPolicyService).validateForUpdate(
                    11L, 1L, "NewPass123", "encoded-old-password");
            verify(passwordPolicyService).recordPasswordChange(
                    org.mockito.ArgumentMatchers.eq(11L),
                    org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq("encoded-old-password"),
                    any(LocalDateTime.class));
            stp.verify(() -> StpUtil.kickout(11L));
        }
    }
}
