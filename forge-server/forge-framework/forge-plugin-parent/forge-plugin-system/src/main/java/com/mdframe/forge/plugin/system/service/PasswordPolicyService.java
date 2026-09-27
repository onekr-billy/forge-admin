package com.mdframe.forge.plugin.system.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.plugin.system.mapper.SysUserPasswordHistoryMapper;
import com.mdframe.forge.starter.auth.util.PasswordUtil;
import com.mdframe.forge.starter.config.config.SecurityConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Central password-complexity policy used by every explicit password write path.
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final int DEFAULT_MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final int MAX_HISTORY_COUNT = 24;

    private final ConfigManagerService configManagerService;
    private SysUserPasswordHistoryMapper passwordHistoryMapper;

    @Autowired
    void configurePasswordHistoryMapper(SysUserPasswordHistoryMapper passwordHistoryMapper) {
        this.passwordHistoryMapper = passwordHistoryMapper;
    }

    public void validate(String password) {
        if (StrUtil.isBlank(password)) {
            throw new BusinessException("密码不能为空");
        }
        SecurityConfig.PasswordPolicyConfig policy = resolvePolicy();
        int minLength = policy.getMinLength() == null || policy.getMinLength() < 1
                ? DEFAULT_MIN_LENGTH : policy.getMinLength();
        if (password.length() < minLength || password.length() > MAX_LENGTH) {
            throw new BusinessException("密码长度必须在" + minLength + "到" + MAX_LENGTH + "位之间");
        }
        if (Boolean.TRUE.equals(policy.getRequireUppercase()) && password.chars().noneMatch(Character::isUpperCase)) {
            throw new BusinessException("密码必须包含大写字母");
        }
        if (Boolean.TRUE.equals(policy.getRequireLowercase()) && password.chars().noneMatch(Character::isLowerCase)) {
            throw new BusinessException("密码必须包含小写字母");
        }
        if (Boolean.TRUE.equals(policy.getRequireNumbers()) && password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException("密码必须包含数字");
        }
        if (Boolean.TRUE.equals(policy.getRequireSpecialChars())
                && password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))) {
            throw new BusinessException("密码必须包含特殊字符");
        }
    }

    /**
     * Validates complexity and rejects reuse of the current or retained historical password.
     */
    public void validateForUpdate(Long userId, Long tenantId, String password, String currentPasswordHash) {
        validate(password);
        if (StrUtil.isNotBlank(currentPasswordHash) && PasswordUtil.matches(password, currentPasswordHash)) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        int historyCount = resolveHistoryCount();
        if (historyCount == 0 || userId == null || tenantId == null || passwordHistoryMapper == null) {
            return;
        }
        List<String> passwordHashes = passwordHistoryMapper.selectRecentPasswordHashes(
                tenantId, userId, historyCount);
        if (passwordHashes != null && passwordHashes.stream()
                .filter(StrUtil::isNotBlank)
                .anyMatch(passwordHash -> PasswordUtil.matches(password, passwordHash))) {
            throw new BusinessException("新密码不能与最近使用过的密码相同");
        }
    }

    /**
     * Stores the replaced hash and trims hashes outside the configured reuse window.
     * Must be invoked in the same transaction as the password update.
     */
    public void recordPasswordChange(Long userId, Long tenantId, String previousPasswordHash,
                                     LocalDateTime changedTime) {
        if (userId == null || tenantId == null || StrUtil.isBlank(previousPasswordHash)
                || passwordHistoryMapper == null) {
            return;
        }
        int historyCount = resolveHistoryCount();
        if (historyCount == 0) {
            passwordHistoryMapper.deleteAllPasswordHistory(tenantId, userId);
            return;
        }
        LocalDateTime effectiveChangedTime = changedTime == null ? LocalDateTime.now() : changedTime;
        passwordHistoryMapper.insertPasswordHistory(
                tenantId, userId, previousPasswordHash, effectiveChangedTime);
        passwordHistoryMapper.deleteOlderPasswordHistory(tenantId, userId, historyCount);
    }

    /** Applies password-age policy only to password-based authentication strategies. */
    public void applyPasswordExpiration(LoginUser loginUser) {
        if (loginUser != null && isExpired(loginUser.getPasswordChangedTime(), loginUser.getCreateTime())) {
            loginUser.setForcePasswordChange(true);
        }
    }

    public boolean isExpired(LocalDateTime passwordChangedTime, LocalDateTime accountCreatedTime) {
        return isExpired(passwordChangedTime, accountCreatedTime, LocalDateTime.now());
    }

    boolean isExpired(LocalDateTime passwordChangedTime, LocalDateTime accountCreatedTime, LocalDateTime now) {
        Integer configuredDays = resolvePolicy().getExpireDays();
        if (configuredDays == null || configuredDays <= 0) {
            return false;
        }
        LocalDateTime credentialTime = passwordChangedTime == null ? accountCreatedTime : passwordChangedTime;
        return credentialTime == null || !credentialTime.isAfter(now.minusDays(configuredDays));
    }

    /** Generates an unknown, non-interactive credential for social-only users. */
    public String generateSystemCredential() {
        SecurityConfig.PasswordPolicyConfig policy = resolvePolicy();
        int configuredMinimum = policy.getMinLength() == null ? DEFAULT_MIN_LENGTH : policy.getMinLength();
        int length = Math.min(MAX_LENGTH, Math.max(64, configuredMinimum));
        StringBuilder value = new StringBuilder("Aa1!");
        while (value.length() < length) {
            value.append(IdUtil.fastSimpleUUID());
        }
        String credential = value.substring(0, length);
        validate(credential);
        return credential;
    }

    private SecurityConfig.PasswordPolicyConfig resolvePolicy() {
        SecurityConfig config = configManagerService == null ? null : configManagerService.getSecurityConfig();
        if (config == null || config.getPasswordPolicy() == null) {
            return new SecurityConfig.PasswordPolicyConfig();
        }
        return config.getPasswordPolicy();
    }

    private int resolveHistoryCount() {
        Integer configuredCount = resolvePolicy().getHistoryCount();
        if (configuredCount == null || configuredCount <= 0) {
            return 0;
        }
        return Math.min(configuredCount, MAX_HISTORY_COUNT);
    }
}
