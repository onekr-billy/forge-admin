package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

/**
 * 页面表单数据存储准备结果。
 */
@Data
public class BusinessApplicationFormDataVO {

    private String formAssetId;

    private Long objectId;

    private String objectCode;

    private String objectName;

    private String configKey;

    private Boolean created;

    /** 内容与上次准备一致时为 true：后端跳过了设计器保存与 DDL 同步，前端无需刷新运行配置。 */
    private Boolean unchanged;

    /** DDL 同步失败时的警告信息；表单设计和运行时配置已保存，用户可稍后在高级数据设置中同步。 */
    private String ddlWarning;
}
