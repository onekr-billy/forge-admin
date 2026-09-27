package com.mdframe.forge.plugin.system.service;

import com.mdframe.forge.plugin.system.mapper.SysUserPasswordHistoryMapper;
import com.mdframe.forge.starter.auth.util.PasswordUtil;
import com.mdframe.forge.starter.config.config.SecurityConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordPolicyServiceTest {

    @Test
    void generatedSocialCredentialSatisfiesDefaultPolicy() {
        PasswordPolicyService service = new PasswordPolicyService(null);

        String credential = service.generateSystemCredential();

        assertTrue(credential.length() >= 64);
        assertDoesNotThrow(() -> service.validate(credential));
    }

    @Test
    void rejectsWeakCurrentAndHistoricalPasswords() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.getPasswordPolicy().setHistoryCount(2);
        when(configManagerService.getSecurityConfig()).thenReturn(securityConfig);
        SysUserPasswordHistoryMapper historyMapper = mock(SysUserPasswordHistoryMapper.class);
        PasswordPolicyService service = new PasswordPolicyService(configManagerService);
        service.configurePasswordHistoryMapper(historyMapper);

        String currentHash = PasswordUtil.encrypt("Current123");
        String historicalHash = PasswordUtil.encrypt("Previous123");
        when(historyMapper.selectRecentPasswordHashes(1L, 11L, 2))
                .thenReturn(List.of(historicalHash));

        assertThatThrownBy(() -> service.validateForUpdate(11L, 1L, "weak", currentHash))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码长度");
        assertThatThrownBy(() -> service.validateForUpdate(11L, 1L, "Current123", currentHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("新密码不能与当前密码相同");
        assertThatThrownBy(() -> service.validateForUpdate(11L, 1L, "Previous123", currentHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("新密码不能与最近使用过的密码相同");
        assertDoesNotThrow(() -> service.validateForUpdate(11L, 1L, "FreshPass123", currentHash));
    }

    @Test
    void recordsReplacedHashAndPrunesRetentionWindow() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.getPasswordPolicy().setHistoryCount(5);
        when(configManagerService.getSecurityConfig()).thenReturn(securityConfig);
        SysUserPasswordHistoryMapper historyMapper = mock(SysUserPasswordHistoryMapper.class);
        PasswordPolicyService service = new PasswordPolicyService(configManagerService);
        service.configurePasswordHistoryMapper(historyMapper);
        LocalDateTime changedTime = LocalDateTime.of(2026, 9, 28, 1, 0);

        service.recordPasswordChange(11L, 1L, "encoded-old", changedTime);

        verify(historyMapper).insertPasswordHistory(1L, 11L, "encoded-old", changedTime);
        verify(historyMapper).deleteOlderPasswordHistory(1L, 11L, 5);
    }

    @Test
    void marksExpiredPasswordForMandatoryChange() {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.getPasswordPolicy().setExpireDays(90);
        when(configManagerService.getSecurityConfig()).thenReturn(securityConfig);
        PasswordPolicyService service = new PasswordPolicyService(configManagerService);
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 1, 0);

        assertThat(service.isExpired(now.minusDays(91), null, now)).isTrue();
        assertThat(service.isExpired(now.minusDays(30), null, now)).isFalse();

        LoginUser loginUser = new LoginUser();
        loginUser.setPasswordChangedTime(LocalDateTime.now().minusDays(91));
        loginUser.setForcePasswordChange(false);
        service.applyPasswordExpiration(loginUser);
        assertThat(loginUser.getForcePasswordChange()).isTrue();
    }
}
