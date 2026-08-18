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

    /**
     * 画布草稿 JSON（仅 Mapper 填充，Controller 层返回前由 Service 清理为 null）。
     */
    private String draftSchemaJson;
}
