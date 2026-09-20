package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;

public final class DataAuditTenantSupport {

    private DataAuditTenantSupport() {
    }

    public static Long currentTenantId() {
        Long tenantId = currentTenantIdOrNull();
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    public static Long currentTenantIdOrNull() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId > 0) {
            return tenantId;
        }
        try {
            return SessionHelper.getTenantId();
        } catch (Exception ex) {
            return null;
        }
    }
}
