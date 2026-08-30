package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

/**
 * 流程模型反查业务对象绑定摘要。
 */
@Data
public class BusinessBindingSummaryVO {

    private Long bindingId;

    private String flowModelKey;

    private String bindingName;

    private String objectCode;

    private String objectName;

    /** 业务对象所属的主业务应用（共享对象取排序最靠前的应用）。 */
    private Long applicationId;

    private String applicationName;

    private String suiteName;

    private Boolean codeApp;

    private String entryRoute;
}
