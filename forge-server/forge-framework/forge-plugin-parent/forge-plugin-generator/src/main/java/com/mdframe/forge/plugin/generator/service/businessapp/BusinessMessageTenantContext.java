package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;

/** Approval callbacks have a verified tenant scope but may have no interactive user session. */
public final class BusinessMessageTenantContext {
    private BusinessMessageTenantContext() { }

    public static Long requireTenantId() {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null) {
            try { tenant = SessionHelper.getTenantId(); }
            catch (RuntimeException noSession) { tenant = null; }
        }
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore())
            throw new BusinessException("企业协同消息缺少隔离的可信租户上下文");
        return tenant;
    }
}
