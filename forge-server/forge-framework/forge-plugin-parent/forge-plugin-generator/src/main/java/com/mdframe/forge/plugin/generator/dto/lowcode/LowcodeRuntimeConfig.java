package com.mdframe.forge.plugin.generator.dto.lowcode;

import lombok.Data;

/**
 * 低代码协议转换后的 AiCrudPage 运行时配置。
 */
@Data
public class LowcodeRuntimeConfig {

    private String configKey;

    private String objectCode;

    private String tableName;

    private String tableComment;

    private String layoutType;

    private String searchSchema;

    private String columnsSchema;

    private String editSchema;

    private String apiConfig;

    private String options;

    private String dictConfig;

    private String desensitizeConfig;

    private String encryptConfig;

    private String transConfig;
}
