package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程表单填报实例快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_flow_form_instance")
public class FlowFormInstance extends TenantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Long entryId;

    private String entryCode;

    private String businessKey;

    private String processInstanceId;

    private String modelKey;

    private String formKey;

    private Long formVersionId;

    private Integer formVersion;

    private String schemaSnapshot;

    private String fieldRegistry;

    private String formData;

    private String dataMode;

    private String objectCode;

    private Long recordId;

    private String title;

    private Long startUserId;

    private String startUserName;

    private Long startDeptId;

    private String startDeptName;

    /**
     * 表单实例状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowFormInstanceStatus}。
     */
    private String status;

    private LocalDateTime submitTime;

    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;
}
