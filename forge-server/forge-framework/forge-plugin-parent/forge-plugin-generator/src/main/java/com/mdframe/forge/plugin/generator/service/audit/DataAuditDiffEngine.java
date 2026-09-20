package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditChangeType;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataAuditDiffEngine {

    private final DataAuditValueNormalizer normalizer;

    public DataAuditDiffEngine(DataAuditValueNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<DataAuditFieldChange> diffRows(Map<String, Object> beforeRow,
                                               Map<String, Object> afterRow,
                                               Map<String, LowcodeFieldSchema> fields,
                                               Long targetObjectId,
                                               Long targetModelId,
                                               String targetRecordId,
                                               String relationKey,
                                               DataAuditSourceType sourceType) {
        Map<String, Object> before = beforeRow == null ? Map.of() : beforeRow;
        Map<String, Object> after = afterRow == null ? Map.of() : afterRow;
        boolean beforeAbsent = beforeRow == null;
        boolean afterAbsent = afterRow == null;
        List<DataAuditFieldChange> changes = new ArrayList<>();
        int totalBytes = 0;
        String rel = relationKey == null ? "" : relationKey;
        for (LowcodeFieldSchema field : fields.values()) {
            if (field == null || StringUtils.isBlank(field.getField())) {
                continue;
            }
            if (normalizer.isSystemColumn(field.getColumnName(), field.getField())) {
                continue;
            }
            Object beforeRaw = beforeAbsent ? DataAuditNormalizedValue.absent() : read(before, field);
            Object afterRaw = afterAbsent ? DataAuditNormalizedValue.absent() : read(after, field);
            DataAuditNormalizedValue beforeValue = beforeAbsent
                    ? DataAuditNormalizedValue.absent()
                    : normalizer.normalize(beforeRaw, field);
            DataAuditNormalizedValue afterValue = afterAbsent
                    ? DataAuditNormalizedValue.absent()
                    : normalizer.normalize(afterRaw, field);
            if (normalizer.equals(beforeValue, afterValue)) {
                continue;
            }
            DataAuditChangeType changeType = resolveChangeType(beforeValue, afterValue, beforeAbsent, afterAbsent);
            totalBytes += beforeValue.getEncodedSize() + afterValue.getEncodedSize();
            if (totalBytes > DataAuditValueNormalizer.MAX_EVENT_BYTES) {
                throw DataAuditErrorCode.AUDIT_VALUE_TOO_LARGE.exception();
            }
            String fieldPath = rel.isEmpty() ? field.getField() : rel + "." + targetRecordId + "." + field.getField();
            changes.add(DataAuditFieldChange.builder()
                    .relationKey(rel)
                    .targetRecordId(targetRecordId)
                    .targetObjectId(targetObjectId)
                    .targetModelId(targetModelId)
                    .fieldPath(fieldPath)
                    .field(field)
                    .changeType(changeType)
                    .sourceType(sourceType == null ? DataAuditSourceType.FORM : sourceType)
                    .before(beforeValue)
                    .after(afterValue)
                    .build());
        }
        return changes;
    }

    public Map<String, LowcodeFieldSchema> indexFields(List<LowcodeFieldSchema> fields) {
        Map<String, LowcodeFieldSchema> index = new LinkedHashMap<>();
        if (fields == null) {
            return index;
        }
        for (LowcodeFieldSchema field : fields) {
            if (field == null || StringUtils.isBlank(field.getField())) {
                continue;
            }
            index.put(field.getField(), field);
            if (StringUtils.isNotBlank(field.getColumnName())) {
                index.putIfAbsent(field.getColumnName(), field);
            }
        }
        return index;
    }

    private DataAuditChangeType resolveChangeType(DataAuditNormalizedValue before,
                                                  DataAuditNormalizedValue after,
                                                  boolean beforeAbsent,
                                                  boolean afterAbsent) {
        if (beforeAbsent && !afterAbsent) {
            return DataAuditChangeType.ADD;
        }
        if (!beforeAbsent && afterAbsent) {
            return DataAuditChangeType.REMOVE;
        }
        if (before.getState() == DataAuditValueState.ABSENT) {
            return DataAuditChangeType.ADD;
        }
        if (after.getState() == DataAuditValueState.ABSENT) {
            return DataAuditChangeType.REMOVE;
        }
        return DataAuditChangeType.UPDATE;
    }

    private Object read(Map<String, Object> row, LowcodeFieldSchema field) {
        if (row.containsKey(field.getField())) {
            return row.get(field.getField());
        }
        if (StringUtils.isNotBlank(field.getColumnName()) && row.containsKey(field.getColumnName())) {
            return row.get(field.getColumnName());
        }
        return null;
    }
}
