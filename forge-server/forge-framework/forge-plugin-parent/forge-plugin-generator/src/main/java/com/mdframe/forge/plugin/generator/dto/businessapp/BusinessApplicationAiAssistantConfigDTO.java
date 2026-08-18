package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 应用 AI 助理绑定配置。 */
@Data
public class BusinessApplicationAiAssistantConfigDTO {

    private Map<String, Object> aiAssistantConfig = new LinkedHashMap<>();
}
