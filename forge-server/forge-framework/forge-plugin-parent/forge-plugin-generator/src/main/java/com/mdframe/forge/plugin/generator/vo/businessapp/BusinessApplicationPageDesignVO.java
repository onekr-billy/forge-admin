package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用内页面形态设计保存结果。
 */
@Data
public class BusinessApplicationPageDesignVO {

    private String pageId;

    private String pageType;

    private String formAssetId;

    private Long objectId;

    private String objectCode;

    private String objectName;

    private String configKey;

    private Boolean objectCreated;

    private Boolean hasBusinessData;

    private Map<String, Object> builder = new LinkedHashMap<>();
}
