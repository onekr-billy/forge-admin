package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 业务应用聚合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_application")
public class AiBusinessApplication extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String applicationCode;

    /** 独立门户访问 slug。 */
    private String portalSlug;

    private String applicationName;

    private String suiteCode;

    private String icon;

    private String description;

    /** 1-启用，0-禁用。 */
    private Integer status;

    /** DRAFT/READY/PUBLISHED/CHANGED。 */
    private String designStatus;

    private Integer lastPublishVersion;

    private LocalDateTime lastPublishTime;

    /** 扩展配置 JSON，不允许保存敏感密钥。 */
    private String options;

    /** 门户主题、水印和导航配置 JSON。 */
    private String portalConfig;

    /** 应用 AI 助理绑定配置 JSON。 */
    private String aiAssistantConfig;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
