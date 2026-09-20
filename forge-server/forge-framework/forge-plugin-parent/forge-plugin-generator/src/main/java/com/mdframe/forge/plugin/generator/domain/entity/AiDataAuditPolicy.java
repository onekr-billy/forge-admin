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

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_data_audit_policy")
public class AiDataAuditPolicy extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long objectId;

    private Integer enabled;

    private Integer reasonRequired;

    private Integer showInDetail;

    private Integer policyVersion;

    private LocalDateTime enabledAt;

    private Integer writeBarrier;

    private String coverageStatus;

    private String coverageJson;

    private String policySnapshot;

    @TableLogic(value = "0", delval = "id")
    private Long delFlag;
}
