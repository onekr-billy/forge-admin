package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.auth.RecoveryChannelSupport;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.auth.domain.RegisterRequest;
import com.mdframe.forge.starter.auth.domain.ResetPasswordRequest;
import com.mdframe.forge.starter.auth.domain.SendResetPasswordCodeRequest;
import com.mdframe.forge.starter.auth.service.ICaptchaService;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.context.AuthProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.config.SmsConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        return new SystemAuthServiceImpl(
                userMapper, captchaService, null, null, null, new AuthProperties(),
                configManagerService, null, cacheService, null, null, null,
                recoveryChannelSupport, null);
    }
}
