package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 流程应用入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_flow_entry")
public class FlowEntry extends TenantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String entryCode;

    private String entryName;

    private String entryDesc;

    private String modelKey;

    private String formKey;

    private Long formVersionId;

    private String dataMode;

    private String objectCode;

    private String configKey;

    private String visibleScope;

    private String titleTemplate;

    private String businessKeyTemplate;

    private String submitStrategy;

    /**
     * 状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowEnableStatus}。
     */
    private Integer status;

    private Integer sort;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<FlowEntryFieldMapping> fieldMappings;
}
