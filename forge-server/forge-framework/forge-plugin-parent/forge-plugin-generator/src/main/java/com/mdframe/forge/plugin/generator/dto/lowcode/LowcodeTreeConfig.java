package com.mdframe.forge.plugin.generator.dto.lowcode;

import lombok.Data;

/**
 * 树形单表运行时配置。
 */
@Data
public class LowcodeTreeConfig {

    private String sourceModelCode;

    private String sourceModelName;

    private String sourceTableName;

    private String sourceConfigKey;

    private String keyField;

    private String parentField;

    private String labelField;

    private String filterField;

    private String targetField;

    private String childrenField;

    private String treeTitle;

    private String loadMode;

    private Boolean enabled;
}
