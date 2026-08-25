package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 应用门户配置保存参数。 */
@Data
public class BusinessApplicationPortalConfigDTO {

    private String portalSlug;

    private Map<String, Object> portalConfig = new LinkedHashMap<>();
}
