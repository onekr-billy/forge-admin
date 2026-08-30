package com.mdframe.forge.flow.client.spi;

import lombok.Data;

import java.util.Map;

/**
 * 流程列表业务展示项。
 */
@Data
public class FlowBusinessListDisplayItem {

    private String businessKey;

    private String processInstanceId;

    private String processDefKey;

    private String processName;

    private String processDefinitionName;

    private String taskId;

    private String taskName;

    private String title;

    private String objectCode;

    private Long recordId;

    private String businessObjectName;

    private String businessSummary;

    /** 业务类型，业务侧据此选择展示策略。 */
    private String businessType;

    /** 发起流程时传入的业务参数，业务侧可按需解释。 */
    private Map<String, Object> businessParams;

    /** 业务侧回填的扩展展示数据。 */
    private Map<String, Object> displayExtensions;
}
