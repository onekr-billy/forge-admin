package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditChangeType;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DataAuditFieldChange {

    String relationKey;
    String targetRecordId;
    Long targetObjectId;
    Long targetModelId;
    String fieldPath;
    LowcodeFieldSchema field;
    DataAuditChangeType changeType;
    DataAuditSourceType sourceType;
    DataAuditNormalizedValue before;
    DataAuditNormalizedValue after;
}
