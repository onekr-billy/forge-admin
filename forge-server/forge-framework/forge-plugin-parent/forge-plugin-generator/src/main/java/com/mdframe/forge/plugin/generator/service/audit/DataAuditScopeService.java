package com.mdframe.forge.plugin.generator.service.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditScope;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditScopeDTO;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditPolicyMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditScopeMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditScopeItemVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataAuditScopeService {

    private final DataAuditScopeMapper scopeMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final DataAuditPolicyMapper policyMapper;

    public List<DataAuditScopeItemVO> listByRole(Long roleId) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        if (roleId == null) {
            throw DataAuditErrorCode.AUDIT_UNSUPPORTED.exception("缺少角色");
        }
        List<DataAuditScopeItemVO> result = new ArrayList<>();
        for (AiDataAuditScope scope : scopeMapper.selectByRoleId(tenantId, roleId)) {
            result.add(toVo(scope));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DataAuditScopeItemVO> replaceRoleScope(Long roleId, DataAuditScopeDTO dto) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        if (roleId == null) {
            throw DataAuditErrorCode.AUDIT_UNSUPPORTED.exception("缺少角色");
        }
        scopeMapper.logicDeleteByRoleId(tenantId, roleId);
        Set<Long> objectIds = new LinkedHashSet<>();
        if (dto != null && dto.getObjectIds() != null) {
            objectIds.addAll(dto.getObjectIds());
        }
        for (Long objectId : objectIds) {
            if (objectId == null) {
                continue;
            }
            AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, objectId);
            AiDataAuditScope scope = new AiDataAuditScope();
            scope.setId(IdWorker.getId());
            scope.setTenantId(tenantId);
            scope.setRoleId(roleId);
            scope.setObjectId(objectId);
            if (object != null) {
                scope.setObjectCode(object.getObjectCode());
                scope.setObjectName(object.getObjectName());
            }
            scope.setDelFlag(0L);
            scope.setCreateBy(SessionHelper.getUserId());
            scope.setCreateTime(LocalDateTime.now());
            scope.setCreateDept(SessionHelper.getMainOrgId());
            scope.setUpdateBy(SessionHelper.getUserId());
            scope.setUpdateTime(scope.getCreateTime());
            scopeMapper.insert(scope);
        }
        return listByRole(roleId);
    }

    public Set<Long> currentUserObjectIds() {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        if (SessionHelper.isAdmin()) {
            return configuredPolicyObjectIds(tenantId);
        }
        List<Long> roleIds = SessionHelper.getRoleIds();
        Set<Long> objectIds = new LinkedHashSet<>();
        for (AiDataAuditScope scope : scopeMapper.selectByRoleIds(tenantId, roleIds)) {
            if (scope.getObjectId() != null) {
                objectIds.add(scope.getObjectId());
            }
        }
        return objectIds;
    }

    public List<DataAuditScopeItemVO> listCurrentUserObjects() {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        if (SessionHelper.isAdmin()) {
            return listConfiguredPolicyObjects(tenantId);
        }
        List<Long> roleIds = SessionHelper.getRoleIds();
        Map<Long, DataAuditScopeItemVO> objects = new LinkedHashMap<>();
        for (AiDataAuditScope scope : scopeMapper.selectByRoleIds(tenantId, roleIds)) {
            if (scope.getObjectId() != null) {
                objects.putIfAbsent(scope.getObjectId(), toVo(scope));
            }
        }
        return new ArrayList<>(objects.values());
    }

    public boolean canAuditObject(Long objectId) {
        return objectId != null && currentUserObjectIds().contains(objectId);
    }

    private Set<Long> configuredPolicyObjectIds(Long tenantId) {
        Set<Long> objectIds = new LinkedHashSet<>();
        for (AiDataAuditPolicy policy : policyMapper.selectConfigured(tenantId)) {
            if (policy.getObjectId() != null) {
                objectIds.add(policy.getObjectId());
            }
        }
        return objectIds;
    }

    private List<DataAuditScopeItemVO> listConfiguredPolicyObjects(Long tenantId) {
        List<DataAuditScopeItemVO> result = new ArrayList<>();
        for (Long objectId : configuredPolicyObjectIds(tenantId)) {
            AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, objectId);
            DataAuditScopeItemVO item = new DataAuditScopeItemVO();
            item.setObjectId(String.valueOf(objectId));
            if (object != null) {
                item.setObjectCode(object.getObjectCode());
                item.setObjectName(object.getObjectName());
            }
            result.add(item);
        }
        return result;
    }

    private DataAuditScopeItemVO toVo(AiDataAuditScope scope) {
        DataAuditScopeItemVO vo = new DataAuditScopeItemVO();
        vo.setObjectId(scope.getObjectId() == null ? null : String.valueOf(scope.getObjectId()));
        vo.setObjectCode(scope.getObjectCode());
        vo.setObjectName(scope.getObjectName());
        return vo;
    }
}
