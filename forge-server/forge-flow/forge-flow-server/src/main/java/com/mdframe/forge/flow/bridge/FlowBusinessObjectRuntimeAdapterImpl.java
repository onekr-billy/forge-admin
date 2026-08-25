package com.mdframe.forge.flow.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.entity.FlowEntry;
import com.mdframe.forge.starter.flow.entity.FlowEntryFieldMapping;
import com.mdframe.forge.starter.flow.service.FlowBusinessObjectRuntimeAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow 服务内的低代码业务对象落表桥接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBusinessObjectRuntimeAdapterImpl implements FlowBusinessObjectRuntimeAdapter {

    private final DynamicCrudService dynamicCrudService;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final ObjectMapper objectMapper;

    @Override
    public BusinessRecordCreateResult createBusinessRecord(FlowEntry entry,
                                                           List<FlowEntryFieldMapping> mappings,
                                                           Map<String, Object> formData) {
        String configKey = firstText(entry.getConfigKey(), entry.getObjectCode());
        if (!StringUtils.hasText(configKey)) {
            throw new RuntimeException("业务对象入口缺少 configKey/objectCode");
        }
        Map<String, Object> recordData = buildRecordData(mappings, formData);
        if (recordData.isEmpty()) {
            throw new RuntimeException("业务对象字段映射为空，无法落表");
        }
        recordData.putIfAbsent("documentStatus", BusinessDocumentFlowStatus.IN_PROCESS.getCode());
        Map<String, Object> saved = dynamicCrudService.insertInternal(configKey, recordData);
        Long recordId = resolveRecordId(saved);
        if (recordId == null) {
            throw new RuntimeException("业务对象落表成功但未返回记录ID");
        }
        String objectCode = firstText(entry.getObjectCode(), configKey);
        String businessKey = objectCode + ":" + recordId;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.putAll(recordData);
        variables.put("objectCode", objectCode);
        variables.put("configKey", configKey);
        variables.put("recordId", recordId);
        variables.put("businessKey", businessKey);

        BusinessRecordCreateResult result = new BusinessRecordCreateResult();
        result.setObjectCode(objectCode);
        result.setRecordId(recordId);
        result.setBusinessKey(businessKey);
        result.setVariables(variables);
        return result;
    }

    @Override
    public void afterProcessStarted(FlowEntry entry,
                                    BusinessRecordCreateResult record,
                                    String processInstanceId,
                                    Map<String, Object> variables) {
        if (record == null || record.getRecordId() == null || !StringUtils.hasText(processInstanceId)) {
            return;
        }
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(resolveTenantId());
        link.setObjectCode(firstText(record.getObjectCode(), entry.getObjectCode(), entry.getConfigKey()));
        link.setRecordId(record.getRecordId());
        link.setBusinessKey(firstText(record.getBusinessKey(), link.getObjectCode() + ":" + record.getRecordId()));
        link.setFlowModelKey(entry.getModelKey());
        link.setProcessInstanceId(processInstanceId);
        link.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        link.setStartUserId(resolveUserId());
        link.setStartTime(LocalDateTime.now());
        link.setVariablesSnapshot(toJson(variables));
        flowInstanceLinkMapper.insert(link);
    }

    private Map<String, Object> buildRecordData(List<FlowEntryFieldMapping> mappings, Map<String, Object> formData) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (mappings != null) {
            for (FlowEntryFieldMapping mapping : mappings) {
                if (mapping == null
                        || (!"BUSINESS_FIELD".equals(mapping.getTargetType())
                        && !"FLOW_AND_BUSINESS".equals(mapping.getTargetType()))) {
                    continue;
                }
                String targetField = firstText(mapping.getTargetField(), mapping.getFormField());
                if (!StringUtils.hasText(targetField)) {
                    continue;
                }
                data.put(targetField, formData == null ? null : formData.get(mapping.getFormField()));
            }
        }
        if (data.isEmpty() && formData != null) {
            data.putAll(formData);
        }
        return data;
    }

    private Long resolveRecordId(Map<String, Object> saved) {
        Object value = saved == null ? null : saved.get("id");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveTenantId() {
        try {
            Long tenantId = SessionHelper.getTenantId();
            return tenantId == null ? 1L : tenantId;
        } catch (Exception e) {
            return 1L;
        }
    }

    private Long resolveUserId() {
        try {
            LoginUser loginUser = SessionHelper.getLoginUser();
            return loginUser == null ? null : loginUser.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
