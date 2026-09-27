package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;

/**
 * 业务对象参与的应用级流程摘要。
 */
@Data
public class BusinessObjectProcessVO {

    private String id;

    private String processName;

    private String processCode;

    private Integer status;

    private String designStatus;

    private String startNodeType;
}
