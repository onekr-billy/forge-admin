package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

/**
 * 业务待办表单上下文查询参数。
 */
@Data
public class BusinessTaskFormContextQueryDTO {

    private String taskId;

    private String businessKey;

    private String processInstanceId;

    private String processDefKey;

    private String taskDefKey;

    private String objectCode;

    /** 业务对象稳定 ID，优先级高于可能重复的历史 objectCode。 */
    private Long objectId;

    /** 运行配置键，来自流程启动变量或节点表单引用。 */
    private String configKey;

    /** 业务套件编码，兼容历史流程实例按套件定位对象。 */
    private String suiteCode;

    private Long recordId;

    private String formKey;
}
