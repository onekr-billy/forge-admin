package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityBalanceMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLedgerMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLockMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityBalanceVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityLedgerVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityLockVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通用数量台账只读查询服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessQuantityQueryService {

    private final BusinessQuantityBalanceMapper balanceMapper;
    private final BusinessQuantityLedgerMapper ledgerMapper;
    private final BusinessQuantityLockMapper lockMapper;

    public Page<BusinessQuantityBalanceVO> selectBalancePage(BusinessQuantityQueryDTO query) {
        BusinessQuantityQueryDTO effective = query == null ? new BusinessQuantityQueryDTO() : query;
        return balanceMapper.selectBalancePage(new Page<>(effective.getPageNum(), effective.getPageSize()),
                resolveTenantId(), effective);
    }

    public Page<BusinessQuantityLedgerVO> selectLedgerPage(BusinessQuantityQueryDTO query) {
        BusinessQuantityQueryDTO effective = query == null ? new BusinessQuantityQueryDTO() : query;
        return ledgerMapper.selectLedgerPage(new Page<>(effective.getPageNum(), effective.getPageSize()),
                resolveTenantId(), effective);
    }

    public Page<BusinessQuantityLockVO> selectLockPage(BusinessQuantityQueryDTO query) {
        BusinessQuantityQueryDTO effective = query == null ? new BusinessQuantityQueryDTO() : query;
        return lockMapper.selectLockPage(new Page<>(effective.getPageNum(), effective.getPageSize()),
                resolveTenantId(), effective);
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId == null ? 1L : tenantId;
    }
}
