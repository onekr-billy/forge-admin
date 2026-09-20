package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

@Data
public class DataAuditFieldQueryDTO {

    private String accessMode;

    private String fieldCode;

    private String relationKey;

    private String targetRecordId;

    private String taskId;
}
