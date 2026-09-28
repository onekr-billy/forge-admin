package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;

/**
 * 低代码设计、生成和发布链路的可信租户边界。
 *
 * <p>交互请求通常来自会话，异步发布后处理则来自显式租户作用域；两者同时存在时
 * 必须一致，禁止从业务实体或默认租户推导执行身份。</p>
 */
final class LowcodeTenantContext {

    private LowcodeTenantContext() {
    }

    static Long requireTenantId(String operation) {
        Long scopedTenantId = TenantContextHolder.getTenantId();
        Long sessionTenantId;
        try {
            sessionTenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            sessionTenantId = null;
        }
        if (scopedTenantId != null && sessionTenantId != null
                && !scopedTenantId.equals(sessionTenantId)) {
            throw new BusinessException(operation + "租户上下文不一致");
        }
        Long tenantId = scopedTenantId != null ? scopedTenantId : sessionTenantId;
        if (tenantId == null || tenantId <= 0 || TenantContextHolder.isIgnore()) {
            throw new BusinessException(operation + "缺少可信租户上下文");
        }
        return tenantId;
    }

    static Long requireConfigTenant(AiCrudConfig config, String operation) {
        Long tenantId = requireTenantId(operation);
        if (config == null || config.getTenantId() == null || !tenantId.equals(config.getTenantId())) {
            throw new BusinessException(operation + "配置不属于当前租户");
        }
        return tenantId;
    }
}
