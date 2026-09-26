package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;

/**
 * 审批任务表单解析后的稳定业务身份与运行配置快照。
 */
record TaskFormRuntimeContext(String objectCode,
                              Long recordId,
                              String businessKey,
                              String configKey,
                              JSONObject bindingConfig,
                              AiCrudConfig publishedConfig,
                              AiBusinessObject businessObject) {
}
