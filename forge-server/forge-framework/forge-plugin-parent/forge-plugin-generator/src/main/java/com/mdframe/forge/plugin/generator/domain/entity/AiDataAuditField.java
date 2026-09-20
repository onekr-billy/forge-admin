package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_data_audit_field")
public class AiDataAuditField extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long eventId;

    private Long targetObjectId;

    private Long targetModelId;

    private String targetRecordId;

    private String relationKey;

    private String fieldPath;

    private String fieldCode;

    private String columnName;

    private String fieldLabel;

    private String fieldType;

    private String fieldMetadata;

    private String changeType;

    private String sourceType;

    private String beforeState;

    private String afterState;

    private String beforeValue;

    private String afterValue;

    private String beforeDisplay;

    private String afterDisplay;

    private String valueProtection;

    private String keyVersion;
}
