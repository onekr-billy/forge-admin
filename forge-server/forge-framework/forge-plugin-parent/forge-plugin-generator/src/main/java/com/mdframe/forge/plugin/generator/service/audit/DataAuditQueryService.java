package com.mdframe.forge.plugin.generator.service.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditAccess;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditEvent;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditField;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditEventQueryDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditFieldQueryDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditRevealDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditAccessMode;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueProtection;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditAccessMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditEventMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditFieldMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditEventVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditFieldVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditRevealVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditValueViewVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataAuditQueryService {

    private final DataAuditEventMapper eventMapper;
    private final DataAuditFieldMapper fieldMapper;
    private final DataAuditAccessMapper accessMapper;
    private final DataAuditScopeService scopeService;
    private final DataAuditValueProtector valueProtector;
    private final DataAuditPolicyService policyService;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final DynamicCrudService dynamicCrudService;

    public Page<DataAuditEventVO> pageRecordEvents(Long objectId, String recordId,
                                                   DataAuditEventQueryDTO query, int pageNum, int pageSize) {
        DataAuditEventQueryDTO effective = query == null ? new DataAuditEventQueryDTO() : query;
        effective.setAccessMode(DataAuditAccessMode.RECORD.getCode());
        effective.setObjectId(objectId);
        effective.setRecordId(DataAuditRecordIds.normalize(recordId));
        assertRecordAccess(objectId, effective.getRecordId(), effective.getTaskId());
        return pageEvents(effective, pageNum, pageSize);
    }

    public Page<DataAuditEventVO> pageAdminEvents(DataAuditEventQueryDTO query, int pageNum, int pageSize) {
        DataAuditEventQueryDTO effective = query == null ? new DataAuditEventQueryDTO() : query;
        effective.setAccessMode(DataAuditAccessMode.AUDIT.getCode());
        if (effective.getObjectId() == null && StringUtils.isBlank(effective.getStartTime())) {
            effective.setStartTime(LocalDateTime.now().minusDays(30).toString().replace('T', ' '));
            if (effective.getStartTime().length() > 19) {
                effective.setStartTime(effective.getStartTime().substring(0, 19));
            }
        }
        return pageEvents(effective, pageNum, pageSize);
    }

    public DataAuditEventVO getEvent(Long eventId, String accessMode, String taskId) {
        AiDataAuditEvent event = requireEvent(eventId);
        DataAuditAccessMode mode = DataAuditAccessMode.fromCode(accessMode);
        authorizeEvent(event, mode, taskId);
        return toEventVo(event, mode);
    }

    public Page<DataAuditFieldVO> pageFields(Long eventId, DataAuditFieldQueryDTO query, int pageNum, int pageSize) {
        AiDataAuditEvent event = requireEvent(eventId);
        DataAuditAccessMode mode = DataAuditAccessMode.fromCode(query == null ? null : query.getAccessMode());
        authorizeEvent(event, mode, query == null ? null : query.getTaskId());
        List<String> visible = visibleFieldCodes(event, mode);
        Page<AiDataAuditField> page = fieldMapper.selectFieldPage(
                new Page<>(pageNum, capPageSize(pageSize)),
                DataAuditTenantSupport.currentTenantId(),
                eventId,
                query,
                visible);
        Page<DataAuditFieldVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<DataAuditFieldVO> records = new ArrayList<>();
        boolean canReveal = SessionHelper.hasPermission("ai:dataAudit:sensitive");
        for (AiDataAuditField field : page.getRecords()) {
            records.add(toFieldVo(field, false, canReveal));
        }
        result.setRecords(records);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataAuditRevealVO reveal(Long eventId, Long fieldId, DataAuditRevealDTO dto) {
        if (dto == null || StringUtils.isBlank(StringUtils.trimToNull(dto.getReason()))) {
            throw DataAuditErrorCode.AUDIT_REASON_REQUIRED.exception("查看原值必须填写原因");
        }
        if (!SessionHelper.hasPermission("ai:dataAudit:sensitive")) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        AiDataAuditEvent event = requireEvent(eventId);
        DataAuditAccessMode mode = DataAuditAccessMode.fromCode(dto.getAccessMode());
        authorizeEvent(event, mode, null);
        AiDataAuditField field = fieldMapper.selectFieldById(DataAuditTenantSupport.currentTenantId(), eventId, fieldId);
        if (field == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        DataAuditValueProtection protection = DataAuditValueProtection.fromCode(field.getValueProtection());
        if (protection == DataAuditValueProtection.CHANGE_ONLY) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception("该字段不保存可恢复原值");
        }
        AiDataAuditAccess access = new AiDataAuditAccess();
        access.setId(IdWorker.getId());
        access.setTenantId(DataAuditTenantSupport.currentTenantId());
        access.setEventId(eventId);
        access.setFieldId(fieldId);
        access.setActorId(String.valueOf(SessionHelper.getUserId()));
        DataAuditActorResolver.Actor actor = DataAuditActorResolver.current();
        access.setActorName(actor.actorName());
        access.setAccessReason(dto.getReason().trim());
        access.setAccessTime(LocalDateTime.now());
        access.setAccessResult("GRANTED");
        access.setCreateBy(SessionHelper.getUserId());
        access.setCreateTime(access.getAccessTime());
        access.setCreateDept(SessionHelper.getMainOrgId());
        access.setUpdateBy(SessionHelper.getUserId());
        access.setUpdateTime(access.getAccessTime());
        if (accessMapper.insert(access) != 1) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("原值访问留痕失败");
        }
        DataAuditRevealVO vo = new DataAuditRevealVO();
        vo.setFieldId(String.valueOf(field.getId()));
        DataAuditFieldVO projected = toFieldVo(field, true, true);
        vo.setBefore(projected.getBefore());
        vo.setAfter(projected.getAfter());
        return vo;
    }

    private Page<DataAuditEventVO> pageEvents(DataAuditEventQueryDTO query, int pageNum, int pageSize) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        policyService.ensureIndex(tenantId);
        DataAuditAccessMode mode = DataAuditAccessMode.fromCode(query.getAccessMode());
        List<Long> objectIds = null;
        if (mode == DataAuditAccessMode.AUDIT) {
            Set<Long> scoped = scopeService.currentUserObjectIds();
            if (scoped.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
            if (query.getObjectId() != null && !scoped.contains(query.getObjectId())) {
                throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
            }
            objectIds = query.getObjectId() == null ? new ArrayList<>(scoped) : List.of(query.getObjectId());
        }
        Page<AiDataAuditEvent> page = eventMapper.selectEventPage(
                new Page<>(pageNum, capPageSize(pageSize)), tenantId, query, objectIds);
        Page<DataAuditEventVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<DataAuditEventVO> records = new ArrayList<>();
        for (AiDataAuditEvent event : page.getRecords()) {
            records.add(toEventVo(event, mode));
        }
        result.setRecords(records);
        return result;
    }

    private void assertRecordAccess(Long objectId, String recordId, String taskId) {
        if (objectId == null || StringUtils.isBlank(recordId)) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        if (!SessionHelper.hasPermission("ai:dataAudit:record") && !SessionHelper.hasPermission("ai:dataAudit:detail")) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        AiCrudConfig config = resolveConfig(objectId);
        if (config == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        Map<String, Object> record = dynamicCrudService.selectById(config.getConfigKey(), recordId);
        if (record == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        if (StringUtils.isNotBlank(taskId) && record.get("_flowTaskId") != null
                && !taskId.equals(String.valueOf(record.get("_flowTaskId")))) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
    }

    private void authorizeEvent(AiDataAuditEvent event, DataAuditAccessMode mode, String taskId) {
        if (mode == DataAuditAccessMode.RECORD) {
            assertRecordAccess(event.getObjectId(), event.getRecordId(), taskId);
            return;
        }
        if (!SessionHelper.hasPermission("ai:dataAudit:detail") && !SessionHelper.hasPermission("ai:dataAudit:list")) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        if (!scopeService.canAuditObject(event.getObjectId())) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
    }

    private AiDataAuditEvent requireEvent(Long eventId) {
        if (eventId == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        AiDataAuditEvent event = eventMapper.selectEventById(DataAuditTenantSupport.currentTenantId(), eventId);
        if (event == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception();
        }
        return event;
    }

    private DataAuditEventVO toEventVo(AiDataAuditEvent event, DataAuditAccessMode mode) {
        DataAuditEventVO vo = new DataAuditEventVO();
        vo.setId(String.valueOf(event.getId()));
        vo.setObjectId(String.valueOf(event.getObjectId()));
        vo.setObjectCode(event.getObjectCode());
        vo.setObjectName(event.getObjectName());
        vo.setRecordId(event.getRecordId());
        vo.setRecordLabel(event.getRecordLabel());
        vo.setRevision(event.getRevision());
        vo.setEventType(event.getEventType());
        vo.setOccurredAt(event.getOccurredAt());
        vo.setOperationId(event.getOperationId());
        vo.setParentOperationId(event.getParentOperationId());
        vo.setCorrelationId(event.getCorrelationId());
        vo.setSourceType(event.getSourceType());
        vo.setActorType(event.getActorType());
        vo.setActorId(event.getActorId());
        vo.setActorName(event.getActorName());
        vo.setChangeReason(event.getChangeReason());
        vo.setReasonCode(event.getReasonCode());
        vo.setFlowInstanceId(event.getFlowInstanceId());
        vo.setTaskId(event.getTaskId());
        vo.setActionExecutionId(event.getActionExecutionId());
        vo.setChangedFieldCount(event.getChangedFieldCount());
        vo.setChangedRowCount(event.getChangedRowCount());
        List<String> visible = visibleFieldCodes(event, mode);
        int visibleCount = fieldMapper.countVisibleFields(
                DataAuditTenantSupport.currentTenantId(), event.getId(), visible);
        vo.setVisibleFieldCount(visibleCount);
        vo.setCanReveal(SessionHelper.hasPermission("ai:dataAudit:sensitive"));
        vo.getCapabilities().add("expand");
        return vo;
    }

    private DataAuditFieldVO toFieldVo(AiDataAuditField field, boolean reveal, boolean canReveal) {
        DataAuditFieldVO vo = new DataAuditFieldVO();
        vo.setId(String.valueOf(field.getId()));
        vo.setEventId(String.valueOf(field.getEventId()));
        vo.setTargetObjectId(field.getTargetObjectId() == null ? null : String.valueOf(field.getTargetObjectId()));
        vo.setTargetRecordId(field.getTargetRecordId());
        vo.setRelationKey(field.getRelationKey());
        vo.setFieldPath(field.getFieldPath());
        vo.setFieldCode(field.getFieldCode());
        vo.setColumnName(field.getColumnName());
        vo.setFieldLabel(field.getFieldLabel());
        vo.setFieldType(field.getFieldType());
        vo.setChangeType(field.getChangeType());
        vo.setSourceType(field.getSourceType());
        DataAuditValueProtection protection = DataAuditValueProtection.fromCode(field.getValueProtection());
        vo.setValueProtection(protection.getCode());
        vo.setMasked(protection != DataAuditValueProtection.PLAIN && !reveal);
        vo.setCanReveal(canReveal && protection == DataAuditValueProtection.ENCRYPTED);
        LowcodeFieldSchema schema = new LowcodeFieldSchema();
        schema.setField(field.getFieldCode());
        schema.setSensitiveType(protection == DataAuditValueProtection.ENCRYPTED ? "CUSTOM" : "NONE");
        DataAuditValueViewVO before = valueProtector.project(
                field.getBeforeState(), field.getBeforeValue(), field.getBeforeDisplay(),
                field.getFieldType(), protection, schema, reveal);
        DataAuditValueViewVO after = valueProtector.project(
                field.getAfterState(), field.getAfterValue(), field.getAfterDisplay(),
                field.getFieldType(), protection, schema, reveal);
        vo.setBefore(before);
        vo.setAfter(after);
        return vo;
    }

    private List<String> visibleFieldCodes(AiDataAuditEvent event, DataAuditAccessMode mode) {
        if (mode != DataAuditAccessMode.RECORD) {
            return null;
        }
        return null;
    }

    private AiCrudConfig resolveConfig(Long objectId) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, objectId);
        if (object == null || StringUtils.isBlank(object.getConfigKey())) {
            return null;
        }
        return crudConfigMapper.selectByConfigKey(tenantId, object.getConfigKey());
    }

    private int capPageSize(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private Page<DataAuditEventVO> emptyPage(int pageNum, int pageSize) {
        return new Page<>(pageNum, capPageSize(pageSize), 0);
    }
}
