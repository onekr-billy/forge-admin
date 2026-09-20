package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditCursor;
import com.mdframe.forge.plugin.generator.mapper.DataAuditCursorMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditRecordMetaVO;

import java.util.Map;

public final class DataAuditRecordMetaBuilder {

    private DataAuditRecordMetaBuilder() {
    }

    public static void attach(Map<String, Object> record,
                              Long tenantId,
                              AiBusinessObject object,
                              DataAuditCursorMapper cursorMapper) {
        DataAuditRecordMetaVO meta = build(record, tenantId, object, cursorMapper);
        record.put(DataAuditPayloadSupport.PAYLOAD_KEY, meta);
    }

    public static DataAuditRecordMetaVO build(Map<String, Object> record,
                                              Long tenantId,
                                              AiBusinessObject object,
                                              DataAuditCursorMapper cursorMapper) {
        DataAuditRecordMetaVO meta = new DataAuditRecordMetaVO();
        if (object == null || tenantId == null) {
            meta.setConfigured(false);
            meta.setEnabled(false);
            meta.setReasonRequired(false);
            meta.setShowInDetail(false);
            meta.setHistoryAvailable(false);
            meta.setRevision(0L);
            return meta;
        }
        DataAuditPolicyIndex.ObjectPolicy policy = DataAuditTransactionHolder.index()
                .findObject(tenantId, object.getId());
        meta.setObjectId(String.valueOf(object.getId()));
        meta.setConfigured(policy != null);
        meta.setEnabled(policy != null && policy.enabled());
        meta.setReasonRequired(policy != null && policy.reasonRequired());
        meta.setShowInDetail(policy != null && policy.showInDetail());
        Object rawId = record.get("id");
        if (rawId == null) {
            rawId = record.get("ID");
        }
        String recordId = DataAuditRecordIds.normalize(rawId);
        meta.setRecordId(recordId);
        if (policy == null || recordId == null || cursorMapper == null) {
            meta.setHistoryAvailable(false);
            meta.setRevision(0L);
            return meta;
        }
        AiDataAuditCursor cursor = cursorMapper.selectByRecord(tenantId, object.getId(), recordId);
        meta.setRevision(cursor == null || cursor.getRevision() == null ? 0L : cursor.getRevision());
        meta.setHistoryAvailable(meta.getRevision() > 0L);
        return meta;
    }
}
