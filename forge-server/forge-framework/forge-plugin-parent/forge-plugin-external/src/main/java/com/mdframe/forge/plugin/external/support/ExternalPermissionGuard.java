package com.mdframe.forge.plugin.external.support;

import cn.dev33.satoken.exception.SaTokenException;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.springframework.stereotype.Component;

@Component
public class ExternalPermissionGuard {

    public void check(ExternalApi api) {
        if (!Boolean.TRUE.equals(api.getPermissionCheckEnabled())) {
            throw new BusinessException(403, "外部接口未启用调用权限校验，已拒绝执行");
        }
        String permission = api.getRequiredPermission();
        if (permission == null || permission.isBlank()) {
            throw new BusinessException("外部接口已启用权限校验但未配置权限码");
        }
        if (!hasPermission(permission.trim())) {
            throw new BusinessException(403, "无权调用该外部接口");
        }
    }

    private boolean hasPermission(String permission) {
        try {
            return SessionHelper.hasPermission(permission);
        } catch (SaTokenException exception) {
            return false;
        }
    }
}
