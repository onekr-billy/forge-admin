package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用内页面形态设计的原子保存参数。
 */
@Data
public class BusinessApplicationPageDesignDTO {

    private String pageId;

    /** form/list/list-form/custom */
    private String pageType;

    private String formAssetId;

    private Long objectId;

    private String objectCode;

    private String objectName;

    private List<BusinessFieldDTO> fields;

    private FormDesignerSchemaDTO formDesignerSchema;

    /** 完整的 inAppBuilder 草稿。 */
    private Map<String, Object> builder = new LinkedHashMap<>();
}
