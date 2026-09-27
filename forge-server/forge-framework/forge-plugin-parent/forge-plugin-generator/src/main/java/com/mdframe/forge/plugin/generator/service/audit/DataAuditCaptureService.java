package com.mdframe.forge.plugin.generator.service.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditCursor;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditEvent;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditField;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditWriteContextDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditChangeType;
import com.mdframe.forge.plugin.generator.enums.DataAuditEventType;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueProtection;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditCursorMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditEventMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditFieldMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudRepository;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataAuditCaptureService {

    private static final String DEFAULT_PK = "id";

    private final DataAuditCursorMapper cursorMapper;
    private final DataAuditEventMapper eventMapper;
    private final DataAuditFieldMapper fieldMapper;
    private final DataAuditPolicyService policyService;
    private final DataAuditDiffEngine diffEngine;
    private final DataAuditValueProtector valueProtector;
    private final DataAuditFieldResolver fieldResolver;
    private final BusinessObjectMapper businessObjectMapper;
    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;

    public AutoCloseable open(AiCrudConfig config,
                              Object recordId,
                              DataAuditSourceType sourceType,
                              DataAuditEventType eventType,
                              DataAuditWriteContextDTO context,
                              boolean requireRevision) {
        if (config == null) {
            return () -> {
            };
        }
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        AiBusinessObject object = resolveObject(tenantId, config);
        policyService.ensureIndex(tenantId);
        DataAuditPolicyIndex.TableBinding tableBinding = tenantId == null || config == null
                ? null
                : DataAuditTransactionHolder.index().findTable(tenantId, config.getTableName());
        if (object == null) {
            // 表已进入审计索引，但运行配置找不到业务对象时不能静默跳过，否则写库阶段会报“缺少采集上下文”。
            if (tableBinding != null) {
                throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception(
                        "表「" + config.getTableName() + "」已启用数据变更审计，"
                                + "但运行配置「" + StringUtils.defaultString(config.getConfigKey())
                                + "」未关联到业务对象。"
                                + "请检查业务对象的 configKey 是否与运行配置一致；"
                                + "或到「数据审计」关闭该对象审计后再写入。");
            }
            return () -> {
            };
        }
        DataAuditPolicyIndex.ObjectPolicy policy = DataAuditTransactionHolder.index().findObject(tenantId, object.getId());
        if (policy == null || !policy.enabled()) {
            return () -> {
            };
        }
        if (policy.writeBarrier()) {
            throw DataAuditErrorCode.AUDIT_UNSUPPORTED.exception("审计策略切换中，请稍后重试");
        }
        DataAuditCaptureSession existing = DataAuditTransactionHolder.current();
        if (existing != null) {
            return () -> {
            };
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("审计必须在事务内采集");
        }
        DataAuditWriteContextDTO safeContext = context == null ? new DataAuditWriteContextDTO() : context;
        String reason = StringUtils.trimToNull(safeContext.getReason());
        DataAuditActorResolver.Actor actor = DataAuditActorResolver.current();
        boolean userWrite = actor.type() == com.mdframe.forge.plugin.generator.enums.DataAuditActorType.USER
                && (eventType == DataAuditEventType.UPDATE || eventType == DataAuditEventType.DELETE)
                && sourceType == DataAuditSourceType.FORM;
        if (userWrite && policy.reasonRequired() && (reason == null || reason.isBlank())) {
            if (eventType == DataAuditEventType.DELETE) {
                throw DataAuditErrorCode.AUDIT_REASON_REQUIRED.exception("请填写删除原因");
            }
            throw DataAuditErrorCode.AUDIT_REASON_REQUIRED.exception();
        }
        if (reason != null && (reason.length() < 1 || reason.length() > 500)) {
            String lengthMessage = eventType == DataAuditEventType.DELETE
                    ? "删除原因长度须为 1-500 个字符"
                    : "修改原因长度须为 1-500 个字符";
            throw DataAuditErrorCode.AUDIT_REASON_REQUIRED.exception(lengthMessage);
        }
        // 删除不走乐观锁：列表/补齐常带过期或 0 revision，会误拦合法删除；修订号仍在落库时递增
        boolean needRevision = requireRevision && userWrite
                && eventType != DataAuditEventType.CREATE
                && eventType != DataAuditEventType.DELETE;
        if (needRevision && safeContext.getExpectedRevision() == null) {
            throw DataAuditErrorCode.AUDIT_REVISION_REQUIRED.exception();
        }
        DataAuditCaptureSession session = new DataAuditCaptureSession(
                tenantId,
                DataAuditTransactionHolder.currentSourceOr(sourceType == null ? DataAuditSourceType.FORM : sourceType),
                eventType == null ? DataAuditEventType.UPDATE : eventType,
                actor,
                reason,
                null,
                safeContext.getExpectedRevision(),
                needRevision,
                null,
                null,
                config.getConfigKey(),
                null,
                null,
                null,
                null,
                new RepositoryRowReader(LowcodeRuntimeDataSourceContextHolder.get()),
                fieldResolver.load(config)
        );
        String normalizedId = DataAuditRecordIds.normalize(recordId);
        if (normalizedId != null && eventType != DataAuditEventType.CREATE) {
            lockCursor(session, object.getId(), normalizedId, needRevision, safeContext.getExpectedRevision());
        }
        DataAuditTransactionHolder.bind(session);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                persist(session, object, config);
            }

            @Override
            public void afterCompletion(int status) {
                DataAuditTransactionHolder.clear();
            }
        });
        return () -> {
        };
    }

    public void attachReadMeta(AiCrudConfig config, Map<String, Object> record) {
        if (config == null || record == null) {
            return;
        }
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        AiBusinessObject object = resolveObject(tenantId, config);
        if (tenantId != null && object != null) {
            policyService.ensureIndex(tenantId);
        }
        DataAuditRecordMetaBuilder.attach(record, tenantId, object, cursorMapper);
    }

    /**
     * 列表批量挂载策略元数据（不含 cursor 查询），供删除入口判断是否走审计批量接口。
     */
    public void attachPolicyMeta(AiCrudConfig config, List<Map<String, Object>> records) {
        if (config == null || records == null || records.isEmpty()) {
            return;
        }
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        AiBusinessObject object = resolveObject(tenantId, config);
        if (tenantId != null && object != null) {
            policyService.ensureIndex(tenantId);
        }
        for (Map<String, Object> record : records) {
            if (record != null) {
                DataAuditRecordMetaBuilder.attachPolicyOnly(record, tenantId, object);
            }
        }
    }

    private void persist(DataAuditCaptureSession session, AiBusinessObject object, AiCrudConfig config) {
        if (session == null || session.isSealed()) {
            return;
        }
        try {
            List<AiDataAuditEvent> pendingEvents = new ArrayList<>();
            List<AiDataAuditField> pendingFields = new ArrayList<>();
            List<CursorRevisionUpdate> cursorUpdates = new ArrayList<>();
            for (DataAuditCaptureSession.AggregateState aggregate : session.getAggregates().values()) {
                collectPersistAggregate(session, object, config, aggregate, pendingEvents, pendingFields, cursorUpdates);
            }
            flushInsertBatch(eventMapper::insertBatch, pendingEvents);
            flushInsertBatch(fieldMapper::insertBatch, pendingFields);
            for (CursorRevisionUpdate update : cursorUpdates) {
                cursorMapper.updateRevision(update.id(), update.revision(), update.lastEventId());
            }
        } catch (RuntimeException ex) {
            log.error("数据审计持久化失败 operationId={}", session.getOperationId(), ex);
            if (ex.getClass().getName().contains("BusinessException")) {
                throw ex;
            }
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception();
        } finally {
            session.seal();
        }
    }

    private void collectPersistAggregate(DataAuditCaptureSession session,
                                         AiBusinessObject openedObject,
                                         AiCrudConfig config,
                                         DataAuditCaptureSession.AggregateState aggregate,
                                         List<AiDataAuditEvent> pendingEvents,
                                         List<AiDataAuditField> pendingFields,
                                         List<CursorRevisionUpdate> cursorUpdates) {
        if (!aggregate.touched) {
            return;
        }
        Long objectId = aggregate.objectId;
        AiBusinessObject object = openedObject != null && openedObject.getId().equals(objectId)
                ? openedObject
                : businessObjectMapper.selectByIdForTenant(session.getTenantId(), objectId);
        if (object == null) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("审计对象不存在");
        }
        List<DataAuditFieldChange> changes = new ArrayList<>();
        java.util.LinkedHashSet<DataAuditCaptureSession.RowKey> keys = new java.util.LinkedHashSet<>();
        keys.addAll(aggregate.before.keySet());
        keys.addAll(aggregate.after.keySet());
        Map<String, Map<String, Map<String, Object>>> childBefore = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> childAfter = new java.util.LinkedHashMap<>();
        Map<String, Object> masterBefore = null;
        Map<String, Object> masterAfter = null;
        for (DataAuditCaptureSession.RowKey rowKey : keys) {
            Map<String, Object> before = aggregate.before.get(rowKey);
            Map<String, Object> afterHint = aggregate.after.get(rowKey);
            Map<String, Object> after;
            if (afterHint == null && aggregate.after.containsKey(rowKey)) {
                after = null;
            } else if (StringUtils.isNotBlank(rowKey.recordId())) {
                after = session.readFinalRow(rowKey);
            } else {
                after = afterHint;
            }
            if (StringUtils.isNotBlank(rowKey.relationKey())) {
                childBefore.computeIfAbsent(rowKey.relationKey(), ignored -> new java.util.LinkedHashMap<>())
                        .put(rowKey.recordId(), before);
                childAfter.computeIfAbsent(rowKey.relationKey(), ignored -> new java.util.LinkedHashMap<>())
                        .put(rowKey.recordId(), after);
                continue;
            }
            if (aggregate.key.recordId().equals(rowKey.recordId()) || masterBefore == null && masterAfter == null) {
                masterBefore = before;
                masterAfter = after;
            }
            Map<String, LowcodeFieldSchema> fields = fieldResolver.resolveSnapshotFields(
                    session.getFieldsByTable().get(rowKey.tableName()), before, after);
            DataAuditSourceType fieldSource = aggregate.fieldSources.getOrDefault(rowKey, session.getSourceType());
            changes.addAll(diffEngine.diffRows(
                    before,
                    after,
                    fields,
                    objectId,
                    object.getModelId(),
                    rowKey.recordId(),
                    "",
                    fieldSource));
        }
        java.util.LinkedHashSet<String> childKeys = new java.util.LinkedHashSet<>();
        childKeys.addAll(childBefore.keySet());
        childKeys.addAll(childAfter.keySet());
        for (String relationKey : childKeys) {
            DataAuditChildRowSummarizer.Summary summary = DataAuditChildRowSummarizer.summarize(
                    relationKey,
                    childBefore.getOrDefault(relationKey, Map.of()),
                    childAfter.getOrDefault(relationKey, Map.of()));
            if (!summary.hasChange()) {
                continue;
            }
            changes.add(toChildSummaryChange(objectId, object.getModelId(), aggregate.key.recordId(),
                    relationKey, summary, session.getSourceType()));
        }
        if (changes.isEmpty()) {
            return;
        }
        if (masterBefore == null && masterAfter == null && config != null
                && StringUtils.isNotBlank(config.getTableName())) {
            masterAfter = session.readRecordLabelRow(config.getTableName(), aggregate.key.recordId());
        }
        DataAuditEventType eventType = resolveEventType(session, aggregate, changes);
        AiDataAuditCursor cursor = lockCursor(session, objectId, aggregate.key.recordId(),
                session.isRequireExpectedRevision(), session.getExpectedRevision());
        long nextRevision = cursor.getRevision() == null ? 1L : cursor.getRevision() + 1L;
        AiDataAuditEvent event = new AiDataAuditEvent();
        event.setId(IdWorker.getId());
        event.setTenantId(session.getTenantId());
        event.setObjectId(objectId);
        event.setObjectCode(object.getObjectCode());
        event.setObjectName(object.getObjectName());
        event.setRecordId(aggregate.key.recordId());
        event.setRecordLabel(DataAuditRecordLabelResolver.resolve(
                object,
                config,
                masterBefore,
                masterAfter,
                session.getFieldsByTable().getOrDefault(config.getTableName(), Map.of()).values(),
                aggregate.key.recordId(),
                objectMapper));
        event.setRevision(nextRevision);
        event.setEventType(eventType.getCode());
        event.setOccurredAt(LocalDateTime.now());
        event.setOperationId(session.getOperationId());
        event.setParentOperationId(session.getParentOperationId());
        event.setCorrelationId(session.getCorrelationId());
        event.setSourceType(session.getSourceType().getCode());
        event.setConfigKey(session.getConfigKey());
        event.setPolicyVersion(policyVersion(session.getTenantId(), objectId));
        event.setActorType(session.getActor().type().getCode());
        event.setActorId(session.getActor().actorId());
        event.setActorName(session.getActor().actorName());
        event.setClientId(session.getActor().clientId());
        event.setDelegatedUserId(session.getActor().delegatedUserId());
        event.setChangeReason(session.getReason());
        event.setReasonCode(session.getReasonCode());
        event.setFlowInstanceId(session.getFlowInstanceId());
        event.setTaskId(session.getTaskId());
        event.setActionExecutionId(session.getActionExecutionId());
        event.setChangedFieldCount(changes.size());
        event.setChangedRowCount((int) changes.stream().map(DataAuditFieldChange::getTargetRecordId).distinct().count());
        event.setCreateBy(session.currentUserId());
        event.setCreateDept(session.currentDeptId());
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateBy(session.currentUserId());
        event.setUpdateTime(event.getCreateTime());
        pendingEvents.add(event);
        for (DataAuditFieldChange change : changes) {
            pendingFields.add(buildFieldEntity(session, event, change));
        }
        cursorUpdates.add(new CursorRevisionUpdate(cursor.getId(), nextRevision, event.getId()));
    }

    private <T> void flushInsertBatch(java.util.function.ToIntFunction<List<T>> inserter, List<T> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        final int chunkSize = 200;
        for (int from = 0; from < items.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, items.size());
            List<T> chunk = items.subList(from, to);
            // MySQL 多值 INSERT 常返回 1 或 SUCCESS_NO_INFO(-2)，不能按 chunk.size() 严格比对
            int affected = inserter.applyAsInt(chunk);
            if (affected == 0) {
                throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception();
            }
        }
    }

    private AiDataAuditField buildFieldEntity(DataAuditCaptureSession session, AiDataAuditEvent event, DataAuditFieldChange change) {
        LowcodeFieldSchema field = change.getField();
        DataAuditValueProtection protection = valueProtector.resolve(field);
        DataAuditValueProtector.StoredValue before = valueProtector.store(change.getBefore(), protection);
        DataAuditValueProtector.StoredValue after = valueProtector.store(change.getAfter(), protection);
        AiDataAuditField entity = new AiDataAuditField();
        entity.setId(IdWorker.getId());
        entity.setTenantId(session.getTenantId());
        entity.setEventId(event.getId());
        entity.setTargetObjectId(change.getTargetObjectId());
        entity.setTargetModelId(change.getTargetModelId());
        entity.setTargetRecordId(change.getTargetRecordId());
        entity.setRelationKey(change.getRelationKey() == null ? "" : change.getRelationKey());
        entity.setFieldPath(change.getFieldPath());
        entity.setFieldCode(field == null ? change.getFieldPath() : field.getField());
        entity.setColumnName(field == null ? null : field.getColumnName());
        entity.setFieldLabel(field == null ? entity.getFieldCode() : field.getLabel());
        entity.setFieldType(change.getAfter() != null ? change.getAfter().getType() : change.getBefore().getType());
        entity.setChangeType(change.getChangeType().getCode());
        entity.setSourceType(change.getSourceType().getCode());
        entity.setBeforeState(before.state());
        entity.setAfterState(after.state());
        entity.setBeforeValue(before.encoded());
        entity.setAfterValue(after.encoded());
        entity.setBeforeDisplay(before.display());
        entity.setAfterDisplay(after.display());
        entity.setValueProtection(protection.getCode());
        entity.setKeyVersion(StringUtils.defaultIfBlank(after.keyVersion(), before.keyVersion()));
        entity.setCreateBy(session.currentUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setCreateDept(session.currentDeptId());
        entity.setUpdateBy(session.currentUserId());
        entity.setUpdateTime(entity.getCreateTime());
        if (field != null && StringUtils.isNotBlank(field.getDictType())) {
            try {
                entity.setFieldMetadata(objectMapper.writeValueAsString(Map.of(
                        "dictType", field.getDictType(),
                        "beforeDisplay", StringUtils.defaultString(change.getBefore().getDisplay()),
                        "afterDisplay", StringUtils.defaultString(change.getAfter().getDisplay())
                )));
            } catch (Exception ignored) {
                // 解释快照失败不阻断主证据
            }
        }
        return entity;
    }

    private record CursorRevisionUpdate(Long id, Long revision, Long lastEventId) {
    }

    private DataAuditFieldChange toChildSummaryChange(Long objectId,
                                                      Long modelId,
                                                      String recordId,
                                                      String relationKey,
                                                      DataAuditChildRowSummarizer.Summary summary,
                                                      DataAuditSourceType sourceType) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(DataAuditChildRowSummarizer.FIELD_CODE);
        field.setColumnName(DataAuditChildRowSummarizer.FIELD_CODE);
        field.setLabel("子表行变更");
        field.setDataType("json");
        field.setBusinessFieldType("JSON");
        String display = DataAuditChildRowSummarizer.display(summary);
        DataAuditNormalizedValue after = DataAuditNormalizedValue.builder()
                .state(DataAuditValueState.VALUE)
                .type(DataAuditChildRowSummarizer.FIELD_TYPE)
                .canonical(summary.toMap())
                .display(display)
                .encodedSize(display.length())
                .build();
        return DataAuditFieldChange.builder()
                .relationKey(relationKey)
                .targetRecordId(recordId)
                .targetObjectId(objectId)
                .targetModelId(modelId)
                .fieldPath(relationKey + "." + DataAuditChildRowSummarizer.FIELD_CODE)
                .field(field)
                .changeType(summary.deleted() > 0 && summary.added() == 0 && summary.updated() == 0
                        ? DataAuditChangeType.REMOVE
                        : DataAuditChangeType.UPDATE)
                .sourceType(sourceType == null ? DataAuditSourceType.FORM : sourceType)
                .before(DataAuditNormalizedValue.absent())
                .after(after)
                .build();
    }

    private AiDataAuditCursor lockCursor(DataAuditCaptureSession session,
                                         Long objectId,
                                         String recordId,
                                         boolean requireRevision,
                                         Long expectedRevision) {
        AiDataAuditCursor cursor = cursorMapper.selectByRecordForUpdate(session.getTenantId(), objectId, recordId);
        if (cursor == null) {
            cursor = new AiDataAuditCursor();
            cursor.setId(IdWorker.getId());
            cursor.setTenantId(session.getTenantId());
            cursor.setObjectId(objectId);
            cursor.setRecordId(recordId);
            cursor.setRevision(0L);
            cursor.setCreateBy(session.currentUserId());
            cursor.setCreateTime(LocalDateTime.now());
            cursor.setCreateDept(session.currentDeptId());
            cursor.setUpdateBy(session.currentUserId());
            cursor.setUpdateTime(cursor.getCreateTime());
            try {
                cursorMapper.insert(cursor);
            } catch (RuntimeException ex) {
                cursor = cursorMapper.selectByRecordForUpdate(session.getTenantId(), objectId, recordId);
                if (cursor == null) {
                    throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("初始化审计游标失败");
                }
            }
        }
        long current = cursor.getRevision() == null ? 0L : cursor.getRevision();
        if (requireRevision && expectedRevision != null && expectedRevision != current) {
            throw DataAuditErrorCode.AUDIT_REVISION_CONFLICT.exception();
        }
        return cursor;
    }

    private DataAuditEventType resolveEventType(DataAuditCaptureSession session,
                                                DataAuditCaptureSession.AggregateState aggregate,
                                                List<DataAuditFieldChange> changes) {
        if (session.getEventType() == DataAuditEventType.DELETE || aggregate.deleteHint) {
            return DataAuditEventType.DELETE;
        }
        boolean created = aggregate.before.values().stream().allMatch(java.util.Objects::isNull);
        if (created || session.getEventType() == DataAuditEventType.CREATE) {
            return DataAuditEventType.CREATE;
        }
        return DataAuditEventType.UPDATE;
    }

    private int policyVersion(Long tenantId, Long objectId) {
        DataAuditPolicyIndex.ObjectPolicy policy = DataAuditTransactionHolder.index().findObject(tenantId, objectId);
        return policy == null ? 1 : policy.policyVersion();
    }

    private AiBusinessObject resolveObject(Long tenantId, AiCrudConfig config) {
        if (tenantId == null || config == null || StringUtils.isBlank(config.getConfigKey())) {
            return null;
        }
        return businessObjectMapper.selectByConfigKey(tenantId, config.getConfigKey());
    }

    private final class RepositoryRowReader implements DataAuditCaptureSession.RowReader {
        private final LowcodeRuntimeDataSourceContext context;

        private RepositoryRowReader(LowcodeRuntimeDataSourceContext context) {
            this.context = context == null ? null : new LowcodeRuntimeDataSourceContext();
            if (context != null) {
                BeanUtils.copyProperties(context, this.context);
            }
        }

        @Override
        public DataAuditCaptureSession.RowReader capture() {
            return new RepositoryRowReader(LowcodeRuntimeDataSourceContextHolder.get());
        }

        @Override
        public String primaryKeyColumn() {
            return context == null || context.getPrimaryKey() == null ? DEFAULT_PK
                    : StringUtils.defaultIfBlank(context.getPrimaryKey().getColumnName(), DEFAULT_PK);
        }

        @Override
        public Map<String, Object> lockAndRead(String tableName, String primaryKeyColumn, Object id) {
            try (var ignored = LowcodeRuntimeDataSourceContextHolder.use(context)) {
                return repository.selectByIdForUpdate(tableName, primaryKeyColumn, id, null);
            }
        }

        @Override
        public Map<String, Object> read(String tableName, String primaryKeyColumn, Object id) {
            // 只在业务行读取期间切换，不能让业务运行上下文泄漏到后续审计落库或其他回调。
            try (var ignored = LowcodeRuntimeDataSourceContextHolder.use(context)) {
                return repository.selectById(tableName, primaryKeyColumn, id, null);
            }
        }
    }
}
