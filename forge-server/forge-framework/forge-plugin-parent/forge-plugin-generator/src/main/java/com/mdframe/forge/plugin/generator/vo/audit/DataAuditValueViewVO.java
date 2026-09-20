package com.mdframe.forge.plugin.generator.vo.audit;

import lombok.Data;

@Data
public class DataAuditValueViewVO {

    private String state;

    private String type;

    private Object value;

    private String display;

    private Boolean protectedValue;

    private Boolean omitted;
}
