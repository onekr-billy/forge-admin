package com.mdframe.forge.plugin.generator.dto.lowcode;

import lombok.Data;

/**
 * AI 应用方案中的业务流程草稿建议。
 *
 * <p>确认初始化后只创建应用级业务流程设计草稿，不会自动部署 Flowable 或生成 BPMN。</p>
 */
@Data
public class LowcodeProcessSuggestionDTO {

    private String processCode;

    private String processName;

    private String processDescription;

    /** 关联 {@link LowcodeDataModelDTO#getModelCode()}。 */
    private String subjectObjectCode;
}
