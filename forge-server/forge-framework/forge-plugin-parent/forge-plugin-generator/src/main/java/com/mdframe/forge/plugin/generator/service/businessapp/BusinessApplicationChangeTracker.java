package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 将受管资产变更传播为应用未发布变更，不反向依赖具体资产服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationChangeTracker {

    private final BusinessApplicationMapper applicationMapper;

    public void markApplicationChanged(Long applicationId) {
        markApplicationChanged(requireTenantId(), applicationId);
    }

    public void markApplicationChanged(Long tenantId, Long applicationId) {
        requireExplicitTenantId(tenantId);
        if (applicationId != null) {
            applicationMapper.markChanged(tenantId, applicationId);
        }
    }

    public void markObjectChanged(Long objectId) {
        markObjectChanged(requireTenantId(), objectId);
    }

    public void markObjectChanged(Long tenantId, Long objectId) {
        requireExplicitTenantId(tenantId);
        if (objectId != null) {
            applicationMapper.markChangedByObjectId(tenantId, objectId);
        }
    }

    private Long requireTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        requireExplicitTenantId(tenantId);
        return tenantId;
    }

    private void requireExplicitTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("应用变更标记缺少可信租户上下文");
        }
    }
}
