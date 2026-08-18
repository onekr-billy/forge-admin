package com.mdframe.forge.plugin.external.service.impl;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.mapper.ExternalApiMapper;
import com.mdframe.forge.plugin.external.service.ExternalProxyService;
import com.mdframe.forge.plugin.external.service.ExternalQuerySourceService;
import com.mdframe.forge.plugin.external.support.ExternalPermissionGuard;
import com.mdframe.forge.plugin.external.support.ExternalQueryContractValidator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExternalQuerySourceServiceImpl implements ExternalQuerySourceService {

    private final ExternalApiMapper apiMapper;
    private final ExternalProxyService proxyService;
    private final ExternalPermissionGuard permissionGuard;
    private final ExternalQueryContractValidator contractValidator;

    @Override
    public List<ExternalApi> listAvailable() {
        return apiMapper.selectLowcodeQuerySources(requireTenantId()).stream()
                .filter(this::hasConfiguredPermission)
                .toList();
    }

    @Override
    public ExternalApi requireMetadata(String sourceKey) {
        SourceKey parsed = parseSourceKey(sourceKey);
        ExternalApi api = apiMapper.selectLowcodeQuerySourceByKey(
                requireTenantId(), parsed.systemCode(), parsed.apiCode());
        if (api == null) {
            throw new BusinessException("低代码外部查询源不存在、未启用或所属系统已停用");
        }
        permissionGuard.check(api);
        return api;
    }

    @Override
    public Object execute(String sourceKey, Map<String, Object> params) {
        ExternalApi api = requireMetadata(sourceKey);
        Map<String, Object> safeParams = contractValidator.validateAndFilter(api.getInputSchemaJson(), params);
        return proxyService.proxyRequest(api.getId(), safeParams);
    }

    @Override
    public String sourceKey(ExternalApi api) {
        if (api == null || isBlank(api.getSystemCode()) || isBlank(api.getApiCode())) {
            throw new BusinessException("外部查询源缺少稳定编码");
        }
        return api.getSystemCode() + "/" + api.getApiCode();
    }

    private boolean hasConfiguredPermission(ExternalApi api) {
        return Boolean.TRUE.equals(api.getPermissionCheckEnabled())
                && !isBlank(api.getRequiredPermission())
                && SessionHelper.hasPermission(api.getRequiredPermission().trim());
    }

    private SourceKey parseSourceKey(String sourceKey) {
        if (isBlank(sourceKey) || sourceKey.length() > 129) {
            throw new BusinessException("低代码外部查询源编码格式不正确");
        }
        int separator = sourceKey.indexOf('/');
        if (separator <= 0 || separator == sourceKey.length() - 1) {
            throw new BusinessException("低代码外部查询源编码格式不正确");
        }
        String systemCode = sourceKey.substring(0, separator).trim();
        String apiCode = sourceKey.substring(separator + 1).trim();
        if (isBlank(systemCode) || isBlank(apiCode)) {
            throw new BusinessException("低代码外部查询源编码格式不正确");
        }
        return new SourceKey(systemCode, apiCode);
    }

    private Long requireTenantId() {
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("低代码查询缺少可信租户上下文");
        }
        return tenantId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SourceKey(String systemCode, String apiCode) {
    }
}
