package com.mdframe.forge.plugin.generator.vo.audit;

import lombok.Data;

@Data
public class DataAuditFieldVO {

    private String id;

    private String eventId;

    private String targetObjectId;

    private String targetRecordId;

    private String relationKey;

    private String fieldPath;

    private String fieldCode;

    private String columnName;

    private String fieldLabel;

    private String fieldType;

    private String changeType;

    private String sourceType;

    private DataAuditValueViewVO before;

    private DataAuditValueViewVO after;

    private String valueProtection;

    private Boolean masked;

    private Boolean canReveal;
}
