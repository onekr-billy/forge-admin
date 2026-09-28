package com.mdframe.forge.plugin.external.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.external.dto.ExternalApiQuery;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.mapper.ExternalApiMapper;
import com.mdframe.forge.plugin.external.mapper.ExternalSystemMapper;
import com.mdframe.forge.plugin.external.service.ExternalApiService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalApiServiceImpl extends ServiceImpl<ExternalApiMapper, ExternalApi>
        implements ExternalApiService {

    private final ExternalApiMapper apiMapper;
    private final ExternalSystemMapper systemMapper;

    @Override
    public IPage<ExternalApi> page(ExternalApiQuery query) {
        query.setTenantId(SessionHelper.getTenantId());
        Page<ExternalApi> page = new Page<>(query.getPageNum(), query.getPageSize());
        return apiMapper.selectApiPage(page, query);
    }

    @Override
    public ExternalApi getManagementById(Long id) {
        return apiMapper.selectApiById(id, SessionHelper.getTenantId());
    }

    @Override
    public ExternalApi getRuntimeById(Long id) {
        return apiMapper.selectApiById(id, SessionHelper.getTenantId());
    }

    @Override
    public List<ExternalApi> listBySystemId(Long systemId) {
        return apiMapper.selectApisBySystemId(systemId, SessionHelper.getTenantId());
    }

    @Override
    public ExternalApi getByCode(String apiCode, Long systemId) {
        return apiMapper.selectApiByCode(apiCode, systemId, SessionHelper.getTenantId());
    }

    @Override
    public List<ExternalApi> listWithSystem() {
        return apiMapper.selectApiListWithSystem(SessionHelper.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveApi(ExternalApi entity) {
        validateTargetSystem(entity);
        entity.setTenantId(SessionHelper.getTenantId());
        return apiMapper.insert(entity) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateApi(ExternalApi entity) {
        if (entity == null || entity.getId() == null) {
            throw new BusinessException("外部接口ID不能为空");
        }
        Long tenantId = SessionHelper.getTenantId();
        if (apiMapper.selectApiById(entity.getId(), tenantId) == null) {
            throw new BusinessException("外部接口不存在");
        }
        validateTargetSystem(entity);
        entity.setTenantId(tenantId);
        return apiMapper.updateById(entity) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeApi(Long id) {
        return apiMapper.deleteApiById(id, SessionHelper.getTenantId()) == 1;
    }

    private void validateTargetSystem(ExternalApi entity) {
        if (entity == null || entity.getSystemId() == null) {
            throw new BusinessException("外部系统不能为空");
        }
        Long tenantId = SessionHelper.getTenantId();
        if (systemMapper.selectSystemById(entity.getSystemId(), tenantId) == null) {
            throw new BusinessException("外部系统不存在");
        }
    }
}
