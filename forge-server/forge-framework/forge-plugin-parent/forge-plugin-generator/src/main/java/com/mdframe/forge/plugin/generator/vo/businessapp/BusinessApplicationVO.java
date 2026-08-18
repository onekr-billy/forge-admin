package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务应用视图。
 */
@Data
public class BusinessApplicationVO {

    private Long id;

    private String applicationCode;

    private String portalSlug;

    private String applicationName;

    private String suiteCode;

    private String suiteName;

    private String icon;

    private String description;

    private Integer status;

    private String designStatus;

    private Integer lastPublishVersion;

    private LocalDateTime lastPublishTime;

    private String options;

    private String portalConfig;

    private String aiAssistantConfig;

    private Long objectCount;

    private Long entryCount;

    private Long activeEntryCount;

    private Long flowCount;

    private Long extensionCount;

    private Long problemCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 创建人，用于“我创建的”与应用可见范围的可信判定。 */
    private Long createBy;

}
