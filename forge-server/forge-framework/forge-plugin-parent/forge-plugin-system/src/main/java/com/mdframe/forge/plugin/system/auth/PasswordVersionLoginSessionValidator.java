package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.auth.session.LoginSessionValidator;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Rejects sessions issued before the user's most recent password change.
 *
 * <p>The database is intentionally the source of truth. This avoids relying on an online-session
 * mirror or a cache invalidation event that can be lost during a multi-instance password reset.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordVersionLoginSessionValidator implements LoginSessionValidator {

    private final SysUserMapper userMapper;

    @Override
    public boolean isValid(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            return false;
        }
        try {
            Long currentVersion = TenantContextHolder.executeIgnore(() ->
                    userMapper.selectActivePasswordVersion(loginUser.getUserId(), loginUser.getTenantId()));
            return currentVersion != null
                    && Objects.equals(normalize(loginUser.getPasswordVersion()), normalize(currentVersion));
        } catch (RuntimeException exception) {
            log.warn("密码凭证版本校验失败，拒绝当前会话: userId={}, exceptionType={}",
                    loginUser.getUserId(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private long normalize(Long version) {
        return version == null ? 0L : version;
    }
}
