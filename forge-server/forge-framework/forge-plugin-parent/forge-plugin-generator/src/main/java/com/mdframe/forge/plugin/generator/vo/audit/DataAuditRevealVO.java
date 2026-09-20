package com.mdframe.forge.plugin.generator.vo.audit;

import lombok.Data;

@Data
public class DataAuditRevealVO {

    private String fieldId;

    private DataAuditValueViewVO before;

    private DataAuditValueViewVO after;
}
