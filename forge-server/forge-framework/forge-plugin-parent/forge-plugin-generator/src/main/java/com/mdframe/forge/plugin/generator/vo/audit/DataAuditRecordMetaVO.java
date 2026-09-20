package com.mdframe.forge.plugin.generator.vo.audit;

import lombok.Data;

@Data
public class DataAuditRecordMetaVO {

    private Boolean configured;

    private Boolean enabled;

    private Boolean reasonRequired;

    private Boolean showInDetail;

    private Boolean historyAvailable;

    private Long revision;

    private String objectId;

    private String recordId;
}
