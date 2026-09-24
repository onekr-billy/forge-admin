package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditEventType;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class DataAuditCaptureSession {

    private final String operationId = UUID.randomUUID().toString().replace("-", "");
    private final Long tenantId;
    private final DataAuditSourceType sourceType;
    private final DataAuditEventType eventType;
    private final DataAuditActorResolver.Actor actor;
    private final String reason;
    private final String reasonCode;
    private final Long expectedRevision;
    private final boolean requireExpectedRevision;
    private final String parentOperationId;
    private final String correlationId;
    private final String configKey;
    private final Long sourceApplicationId;
    private final String flowInstanceId;
    private final String taskId;
    private final String actionExecutionId;
    private final RowReader rowReader;
    private final Map<String, Map<String, LowcodeFieldSchema>> fieldsByTable;
    private final Map<AggregateKey, AggregateState> aggregates = new LinkedHashMap<>();
    private final Map<RowKey, RowRead> rowReads = new LinkedHashMap<>();
    private final Map<String, RowRead> tableReads = new LinkedHashMap<>();
    private boolean sealed;

    public DataAuditCaptureSession(Long tenantId,
                                   DataAuditSourceType sourceType,
                                   DataAuditEventType eventType,
                                   DataAuditActorResolver.Actor actor,
                                   String reason,
                                   String reasonCode,
                                   Long expectedRevision,
                                   boolean requireExpectedRevision,
                                   String parentOperationId,
                                   String correlationId,
                                   String configKey,
                                   Long sourceApplicationId,
                                   String flowInstanceId,
                                   String taskId,
                                   String actionExecutionId,
                                   RowReader rowReader,
                                   Map<String, Map<String, LowcodeFieldSchema>> fieldsByTable) {
        this.tenantId = tenantId;
        this.sourceType = sourceType;
        this.eventType = eventType;
        this.actor = actor;
        this.reason = reason;
        this.reasonCode = reasonCode;
        this.expectedRevision = expectedRevision;
        this.requireExpectedRevision = requireExpectedRevision;
        this.parentOperationId = parentOperationId;
        this.correlationId = correlationId;
        this.configKey = configKey;
        this.sourceApplicationId = sourceApplicationId;
        this.flowInstanceId = flowInstanceId;
        this.taskId = taskId;
        this.actionExecutionId = actionExecutionId;
        this.rowReader = rowReader;
        this.fieldsByTable = fieldsByTable == null ? Map.of() : fieldsByTable;
    }

    public void prepareWrite(DataAuditPolicyIndex.TableBinding binding,
                             String tableName,
                             String primaryKeyColumn,
                             Object recordId,
                             DataAuditTransactionHolder.WriteKind kind) {
        prepareWrite(binding, tableName, primaryKeyColumn, recordId, kind, null);
    }

    public void prepareWrite(DataAuditPolicyIndex.TableBinding binding,
                             String tableName,
                             String primaryKeyColumn,
                             Object recordId,
                             DataAuditTransactionHolder.WriteKind kind,
                             Map<String, Object> beforeSnapshot) {
        assertOpen();
        // 自增主键在 prepare 阶段可能为空，先保存读取信息，afterWrite 再绑定实际行 ID。
        RowRead rowRead = new RowRead(primaryKeyColumn, rowReader.capture());
        tableReads.put(tableName, rowRead);
        AggregateKey key = resolveAggregate(binding, tableName, recordId, null);
        if (key == null) {
            return;
        }
        AggregateState state = aggregates.computeIfAbsent(key, ignored -> new AggregateState(key, binding.objectId()));
        String rowId = DataAuditRecordIds.normalize(recordId);
        RowKey rowKey = new RowKey(tableName, rowId == null ? "" : rowId,
                binding.relationKey() == null ? "" : binding.relationKey());
        rowReads.putIfAbsent(rowKey, rowRead);
        if (kind != DataAuditTransactionHolder.WriteKind.INSERT && rowId != null && !state.before.containsKey(rowKey)) {
            // 批量删除等场景可直接传入已查快照，避免再逐条 SELECT FOR UPDATE
            Map<String, Object> snapshot = beforeSnapshot != null
                    ? beforeSnapshot
                    : rowRead.reader().lockAndRead(tableName, primaryKeyColumn, recordId);
            state.before.put(rowKey, snapshot);
        }
        if (kind == DataAuditTransactionHolder.WriteKind.INSERT) {
            state.before.putIfAbsent(rowKey, null);
        }
    }

    public void afterWrite(DataAuditPolicyIndex.TableBinding binding,
                           String tableName,
                           Object recordId,
                           Map<String, Object> rowHint,
                           DataAuditSourceType fieldSource,
                           DataAuditTransactionHolder.WriteKind kind) {
        assertOpen();
        AggregateKey key = resolveAggregate(binding, tableName, recordId, rowHint);
        if (key == null) {
            return;
        }
        AggregateState state = aggregates.computeIfAbsent(key, ignored -> new AggregateState(key, binding.objectId()));
        String rowId = DataAuditRecordIds.normalize(recordId);
        if (rowId == null) {
            return;
        }
        RowKey rowKey = new RowKey(tableName, rowId, binding.relationKey() == null ? "" : binding.relationKey());
        rowReads.putIfAbsent(rowKey, tableReads.getOrDefault(tableName, defaultRowRead()));
        if (kind == DataAuditTransactionHolder.WriteKind.INSERT) {
            state.before.putIfAbsent(rowKey, null);
        }
        if (kind == DataAuditTransactionHolder.WriteKind.DELETE) {
            state.after.put(rowKey, null);
        } else {
            state.after.put(rowKey, rowHint);
        }
        state.fieldSources.put(rowKey, fieldSource == null ? sourceType : fieldSource);
        state.touched = true;
        if (binding.master()
                && kind == DataAuditTransactionHolder.WriteKind.DELETE
                && eventType != DataAuditEventType.CREATE) {
            state.deleteHint = true;
        }
    }

    public void seal() {
        sealed = true;
    }

    public Map<String, Object> readFinalRow(RowKey rowKey) {
        RowRead read = rowReads.getOrDefault(rowKey, defaultRowRead());
        return read.reader().read(rowKey.tableName(), read.primaryKeyColumn(), rowKey.recordId());
    }

    public Map<String, Object> readRecordLabelRow(String tableName, String recordId) {
        RowRead read = tableReads.getOrDefault(tableName, defaultRowRead());
        return read.reader().read(tableName, read.primaryKeyColumn(), recordId);
    }

    private RowRead defaultRowRead() {
        return new RowRead(rowReader.primaryKeyColumn(), rowReader);
    }

    public Long currentUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception ex) {
            return null;
        }
    }

    public Long currentDeptId() {
        try {
            return SessionHelper.getMainOrgId();
        } catch (Exception ex) {
            return null;
        }
    }

    private AggregateKey resolveAggregate(DataAuditPolicyIndex.TableBinding binding,
                                          String tableName,
                                          Object recordId,
                                          Map<String, Object> rowHint) {
        Long objectId = binding.master() ? binding.objectId() : binding.parentObjectId();
        if (objectId == null) {
            objectId = binding.objectId();
        }
        String aggregateRecordId;
        if (binding.master()) {
            aggregateRecordId = DataAuditRecordIds.normalize(recordId);
        } else {
            aggregateRecordId = DataAuditRecordIds.normalize(readHint(rowHint, binding.fkColumn()));
            if (aggregateRecordId == null) {
                aggregateRecordId = existingMasterRecordId(objectId);
            }
        }
        if (aggregateRecordId == null) {
            return null;
        }
        return new AggregateKey(tenantId, objectId, aggregateRecordId);
    }

    private String existingMasterRecordId(Long objectId) {
        for (AggregateKey key : aggregates.keySet()) {
            if (objectId != null && objectId.equals(key.objectId())) {
                return key.recordId();
            }
        }
        if (aggregates.size() == 1) {
            return aggregates.keySet().iterator().next().recordId();
        }
        return null;
    }

    private Object readHint(Map<String, Object> rowHint, String column) {
        if (rowHint == null || column == null) {
            return null;
        }
        if (rowHint.containsKey(column)) {
            return rowHint.get(column);
        }
        for (Map.Entry<String, Object> entry : rowHint.entrySet()) {
            if (column.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void assertOpen() {
        if (sealed) {
            throw new IllegalStateException("审计采集已封存");
        }
    }

    public interface RowReader {
        /** 为事务提交时的延迟读取保留当前业务数据源上下文。 */
        default RowReader capture() {
            return this;
        }

        default String primaryKeyColumn() {
            return "id";
        }

        Map<String, Object> lockAndRead(String tableName, String primaryKeyColumn, Object id);

        Map<String, Object> read(String tableName, String primaryKeyColumn, Object id);
    }

    public record AggregateKey(Long tenantId, Long objectId, String recordId) {
    }

    public record RowKey(String tableName, String recordId, String relationKey) {
    }

    private record RowRead(String primaryKeyColumn, RowReader reader) {
    }

    public static final class AggregateState {
        public final AggregateKey key;
        public final Long objectId;
        public final Map<RowKey, Map<String, Object>> before = new LinkedHashMap<>();
        public final Map<RowKey, Map<String, Object>> after = new LinkedHashMap<>();
        public final Map<RowKey, DataAuditSourceType> fieldSources = new LinkedHashMap<>();
        public boolean touched;
        public boolean deleteHint;

        public AggregateState(AggregateKey key, Long objectId) {
            this.key = key;
            this.objectId = objectId;
        }
    }
}
