package com.mdframe.forge.plugin.generator.dto.lowcode;

import lombok.Data;

/**
 * 低代码应用发布请求。
 */
@Data
public class LowcodePublishDTO {

    /** SKIP_DDL-不执行DDL，ONLINE_CREATE_TABLE-受控在线建表/追加字段 */
    private String deployMode;

    /** 在线 DDL 二次确认标记 */
    private Boolean confirmOnlineDdl;

    private String menuName;

    private Long menuParentId;

    private Integer menuSort;

    /** 是否同步旧低代码系统菜单；未传保持历史默认行为。 */
    private Boolean syncMenu;

    /** 菜单挂载位置：ADMIN-管理端，MOBILE-移动端，BOTH-两端同时；默认 ADMIN。 */
    private String mountTarget;

    private Long domainId;

    private String domainCode;

    private String domainName;

    private String objectCode;

    private String objectName;

    /** 从业务对象中心发布时用于生成业务应用入口。 */
    private String businessSuiteCode;

    /** 从业务对象中心发布时用于生成业务应用入口。 */
    private String businessObjectCode;

    /** 从业务对象中心发布时用于生成业务应用入口。 */
    private String businessObjectName;

    private String remark;

    /** 允许发布时传入最新草稿，未传则使用已保存草稿 */
    private LowcodeModelSchema modelSchema;

    private LowcodePageSchema pageSchema;
}
