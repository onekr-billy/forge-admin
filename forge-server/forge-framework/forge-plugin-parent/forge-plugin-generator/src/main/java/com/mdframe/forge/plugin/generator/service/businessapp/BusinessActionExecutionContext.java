package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用业务动作执行上下文。
 */
@Data
public class BusinessActionExecutionContext {

    private Long tenantId;

    private String correlationId;

    private AiBusinessObject businessObject;

    private BusinessObjectActionVO action;

    private Integer publishedVersion;

    private String capabilityRequestId;

    private Long capabilityClientId;

    private Long capabilityServiceUserId;

    private String capabilityActorType;

    private BusinessActionExecuteDTO request;

    private Map<String, Object> recordData = new LinkedHashMap<>();

    /** CHILD_ROW 动作的服务端权威父记录；普通动作保持为空。 */
    private Map<String, Object> parentRecordData = new LinkedHashMap<>();

    private Map<String, Object> formData = new LinkedHashMap<>();

    private Map<String, Object> extraContext = new LinkedHashMap<>();

    /** 仅由服务端可信 Session/执行身份构建，客户端同名字段不能覆盖。 */
    private Map<String, Object> systemContext = new LinkedHashMap<>();

    private String executionMode;

    private Map<String, Object> scopedVariables = new LinkedHashMap<>();

    /** 本次执行成功完成的结构化状态迁移摘要，不保存业务原值。 */
    private java.util.List<Map<String, Object>> auditTransitions = new java.util.ArrayList<>();
}
