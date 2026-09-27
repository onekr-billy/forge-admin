package com.mdframe.forge.plugin.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.mdframe.forge.plugin.system.auth.RecoveryChannelSupport;
import com.mdframe.forge.plugin.system.auth.LoginPasswordDecoder;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.service.ISysOnlineUserService;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.plugin.system.service.PasswordPolicyService;
import com.mdframe.forge.plugin.system.entity.SysTenant;
import com.mdframe.forge.plugin.system.mapper.SysTenantMapper;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.auth.domain.RegisterRequest;
import com.mdframe.forge.starter.auth.domain.ResetPasswordRequest;
import com.mdframe.forge.starter.auth.domain.SendResetPasswordCodeRequest;
import com.mdframe.forge.starter.auth.service.ICaptchaService;
import com.mdframe.forge.starter.auth.strategy.AuthStrategyFactory;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.context.AuthProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.config.SmsConfigProvider;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;

class SystemAuthServiceImplPasswordRecoveryTest {

    @Test
    void shouldRejectSendCodeWhenNoChannelEnabled() {
        SystemAuthServiceImpl service = service(
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()),
                mock(ICacheService.class), mock(ICaptchaService.class),
                mock(SysUserMapper.class), mock(ConfigManagerService.class));

        SendResetPasswordCodeRequest request = new SendResetPasswordCodeRequest();
        request.setChannel("sms");
        request.setAccount("13800138000");

        assertThatThrownBy(() -> service.sendResetPasswordCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未启用找回密码通道");
    }

    @Test
    void shouldNotRevealMissingAccountWhenSendingCode() {
        ICaptchaService captchaService = mock(ICaptchaService.class);
        ICacheService cacheService = mock(ICacheService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectByPhoneForLogin("13800138000", null)).thenReturn(null);

        SystemAuthServiceImpl service = service(
                smsSupport(), cacheService, captchaService, userMapper, mock(ConfigManagerService.class));

        SendResetPasswordCodeRequest request = new SendResetPasswordCodeRequest();
        request.setChannel("sms");
        request.setAccount("13800138000");
        service.sendResetPasswordCode(request);

        verify(captchaService, never()).sendSmsCaptcha(anyString());
    }

    @Test
    void shouldSendSmsCodeOnlyWhenChannelEnabledAndUserExists() {
        ICaptchaService captchaService = mock(ICaptchaService.class);
        ICacheService cacheService = mock(ICacheService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(11L);
        when(userMapper.selectByPhoneForLogin("13800138000", 1L)).thenReturn(user);

        SystemAuthServiceImpl service = service(
                smsSupport(), cacheService, captchaService, userMapper, mock(ConfigManagerService.class));

        SendResetPasswordCodeRequest request = new SendResetPasswordCodeRequest();
        request.setChannel("sms");
        request.setAccount("13800138000");
        request.setTenantId(1L);
        service.sendResetPasswordCode(request);

        verify(captchaService).sendSmsCaptcha("13800138000");
    }

    @Test
    void shouldRejectGraphicCaptchaStyleReset() {
        ICaptchaService captchaService = mock(ICaptchaService.class);
        SystemAuthServiceImpl service = service(
                smsSupport(), mock(ICacheService.class), captchaService,
                mock(SysUserMapper.class), mock(ConfigManagerService.class));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setChannel("sms");
        request.setAccount("13800138000");
        request.setCode("123456");
        request.setNewPassword("NewPass123");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误或已过期");
        verify(captchaService, never()).validateAndDelete(any(), any());
        verify(captchaService).validateAndDeleteSmsCaptcha("13800138000", "123456");
    }

    @Test
    void shouldRejectRegisterWhenDisabled() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        when(configManagerService.getLoginConfig()).thenReturn(new LoginConfig());
        SystemAuthServiceImpl service = service(
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()),
                mock(ICacheService.class), mock(ICaptchaService.class),
                mock(SysUserMapper.class), configManagerService);

        assertThatThrownBy(() -> service.register(new RegisterRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("系统未开放注册");
        assertThat(new LoginConfig().getEnableRegister()).isFalse();
    }

    @Test
    void shouldIgnoreUntrustedRegistrationTenantAndUseEnabledDefaultTenant() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setEnableRegister(true);
        when(configManagerService.getLoginConfig()).thenReturn(loginConfig);
        ICaptchaService captchaService = mock(ICaptchaService.class);
        when(captchaService.validateAndDelete("captcha-key", "1234")).thenReturn(true);
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setTenantStatus(1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.countRegistrationConflicts(1L, "safe.user", "13800138000", "safe@example.com"))
                .thenReturn(0L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        SystemAuthServiceImpl service = new SystemAuthServiceImpl(
                userMapper, captchaService, null, null, null, new AuthProperties(),
                configManagerService, null, mock(ICacheService.class), null, null, tenantMapper,
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()), null,
                new PasswordPolicyService(configManagerService));
        RegisterRequest request = validRegisterRequest();
        request.setTenantId(999L);

        LoginUser registered = service.register(request);

        assertThat(registered.getTenantId()).isEqualTo(1L);
        assertThat(request.getTenantId()).isEqualTo(1L);
    }

    @Test
    void shouldRejectRegisterWhenConfirmationDoesNotMatch() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setEnableRegister(true);
        when(configManagerService.getLoginConfig()).thenReturn(loginConfig);
        ICaptchaService captchaService = mock(ICaptchaService.class);
        SystemAuthServiceImpl service = service(
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()),
                mock(ICacheService.class), captchaService, mock(SysUserMapper.class), configManagerService);
        RegisterRequest request = validRegisterRequest();
        request.setConfirmPassword("Different123");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("两次输入的密码不一致");
        verify(captchaService, never()).validateAndDelete(anyString(), anyString());
    }

    @Test
    void shouldRejectWeakRegistrationPasswordBeforeConsumingCaptcha() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setEnableRegister(true);
        when(configManagerService.getLoginConfig()).thenReturn(loginConfig);
        ICaptchaService captchaService = mock(ICaptchaService.class);
        SystemAuthServiceImpl service = service(
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()),
                mock(ICacheService.class), captchaService, mock(SysUserMapper.class), configManagerService);
        RegisterRequest request = validRegisterRequest();
        request.setPassword("weak");
        request.setConfirmPassword("weak");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码长度");
        verify(captchaService, never()).validateAndDelete(anyString(), anyString());
    }

    @Test
    void shouldRevokeAllSessionsAfterPasswordRecovery() {
        ICaptchaService captchaService = mock(ICaptchaService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ISysOnlineUserService onlineUserService = mock(ISysOnlineUserService.class);
        LoginPasswordDecoder passwordDecoder = mock(LoginPasswordDecoder.class);
        when(captchaService.validateAndDeleteSmsCaptcha("13800138000", "123456")).thenReturn(true);
        SysUser user = new SysUser();
        user.setId(11L);
        when(userMapper.selectByPhoneForLogin("13800138000", 1L)).thenReturn(user);
        when(passwordDecoder.decode("NewPass123")).thenReturn("NewPass123");

        SystemAuthServiceImpl service = new SystemAuthServiceImpl(
                userMapper, captchaService, mock(AuthStrategyFactory.class), null, onlineUserService,
                new AuthProperties(), mock(ConfigManagerService.class), null, mock(ICacheService.class),
                null, null, null, smsSupport(), passwordDecoder, new PasswordPolicyService(null)) {
            @Override
            protected boolean updateUserPassword(Long userId, Long tenantId, String encodedPassword) {
                return true;
            }
        };
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setChannel("sms");
        request.setAccount("13800138000");
        request.setCode("123456");
        request.setNewPassword("NewPass123");
        request.setTenantId(1L);

        assertThat(service.resetPassword(request)).isTrue();
        verify(onlineUserService).kickoutAllSessions(11L, null);
    }

    @Test
    void shouldRevokeCurrentSessionByDefaultAfterPasswordChange() {
        AuthProperties authProperties = new AuthProperties();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        IUserLoadService userLoadService = mock(IUserLoadService.class);
        ISysOnlineUserService onlineUserService = mock(ISysOnlineUserService.class);
        when(userLoadService.getUserPassword(11L)).thenReturn("encoded-current-password");
        when(userLoadService.matchPassword("OldPass123", "encoded-current-password")).thenReturn(true);
        when(userMapper.updateActiveUserPassword(anyLong(), anyLong(), anyString(), any())).thenReturn(1);
        LoginUser loginUser = loginUser(3L);

        SystemAuthServiceImpl service = passwordChangeService(
                userMapper, userLoadService, onlineUserService, authProperties);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);

            assertThat(service.changePassword("OldPass123", "NewPass123")).isTrue();
        }

        verify(onlineUserService).kickoutAllSessions(11L, null);
    }

    @Test
    void shouldKeepOnlyCurrentSessionWhenExplicitlyConfigured() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setKeepCurrentSessionAfterPasswordChange(true);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        IUserLoadService userLoadService = mock(IUserLoadService.class);
        ISysOnlineUserService onlineUserService = mock(ISysOnlineUserService.class);
        when(userLoadService.getUserPassword(11L)).thenReturn("encoded-current-password");
        when(userLoadService.matchPassword("OldPass123", "encoded-current-password")).thenReturn(true);
        when(userMapper.updateActiveUserPassword(anyLong(), anyLong(), anyString(), any())).thenReturn(1);
        when(userMapper.selectActivePasswordVersion(11L, 1L)).thenReturn(4L);
        LoginUser loginUser = loginUser(3L);

        SystemAuthServiceImpl service = passwordChangeService(
                userMapper, userLoadService, onlineUserService, authProperties);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);
            stp.when(StpUtil::getTokenValue).thenReturn("current-token");
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertThat(service.changePassword("OldPass123", "NewPass123")).isTrue();

                assertThat(loginUser.getPasswordVersion()).isEqualTo(3L);
                verify(onlineUserService, never()).kickoutAllSessions(anyLong(), any());
                assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
                TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

                session.verify(() -> SessionHelper.setLoginUser(loginUser));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        assertThat(loginUser.getPasswordVersion()).isEqualTo(4L);
        verify(onlineUserService).kickoutAllSessions(11L, "current-token");
    }

    private RecoveryChannelSupport smsSupport() {
        SmsConfigProvider.SmsConfig config = new SmsConfigProvider.SmsConfig();
        config.setStatus(1);
        config.setAccessKeyId("ak");
        config.setTemplateId("tpl");
        return new RecoveryChannelSupport(Optional.of(() -> config), Optional.empty());
    }

    private SystemAuthServiceImpl service(RecoveryChannelSupport recoveryChannelSupport,
                                          ICacheService cacheService,
                                          ICaptchaService captchaService,
                                          SysUserMapper userMapper,
                                          ConfigManagerService configManagerService) {
        when(cacheService.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        return new SystemAuthServiceImpl(
                userMapper, captchaService, null, null, null, new AuthProperties(),
                configManagerService, null, cacheService, null, null, null,
                recoveryChannelSupport, null, new PasswordPolicyService(configManagerService));
    }

    private SystemAuthServiceImpl passwordChangeService(SysUserMapper userMapper,
                                                        IUserLoadService userLoadService,
                                                        ISysOnlineUserService onlineUserService,
                                                        AuthProperties authProperties) {
        return new SystemAuthServiceImpl(
                userMapper, mock(ICaptchaService.class), mock(AuthStrategyFactory.class),
                userLoadService, onlineUserService, authProperties, mock(ConfigManagerService.class),
                null, mock(ICacheService.class), null, null, null,
                new RecoveryChannelSupport(Optional.empty(), Optional.empty()), null,
                new PasswordPolicyService(null));
    }

    private LoginUser loginUser(Long passwordVersion) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(11L);
        loginUser.setTenantId(1L);
        loginUser.setPasswordVersion(passwordVersion);
        return loginUser;
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("safe.user");
        request.setPassword("NewPass123");
        request.setConfirmPassword("NewPass123");
        request.setPhone("13800138000");
        request.setEmail("safe@example.com");
        request.setCodeKey("captcha-key");
        request.setCode("1234");
        return request;
    }
}
