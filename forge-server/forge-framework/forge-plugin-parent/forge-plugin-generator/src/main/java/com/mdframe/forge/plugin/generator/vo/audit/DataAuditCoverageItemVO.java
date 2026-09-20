package com.mdframe.forge.plugin.generator.vo.audit;

import lombok.Data;

@Data
public class DataAuditCoverageItemVO {

    private String code;

    private String label;

    private Boolean passed;

    private String message;
}
