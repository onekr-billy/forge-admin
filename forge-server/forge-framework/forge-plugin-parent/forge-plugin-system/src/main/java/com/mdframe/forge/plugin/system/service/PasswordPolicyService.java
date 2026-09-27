package com.mdframe.forge.plugin.system.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.config.config.SecurityConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Central password-complexity policy used by every explicit password write path.
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final int DEFAULT_MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private final ConfigManagerService configManagerService;

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
}
