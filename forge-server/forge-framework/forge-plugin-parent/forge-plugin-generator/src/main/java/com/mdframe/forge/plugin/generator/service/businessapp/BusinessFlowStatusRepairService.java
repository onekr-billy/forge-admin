package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Selects the writable runtime snapshot and repairs the dedicated flow-status field. */
@Slf4j
final class BusinessFlowStatusRepairService {

    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessDocumentConfigService documentConfigService;

    BusinessFlowStatusRepairService(BusinessRuntimeConfigResolver runtimeConfigResolver,
                                    DynamicCrudService dynamicCrudService,
                                    BusinessDocumentConfigService documentConfigService) {
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.dynamicCrudService = dynamicCrudService;
        this.documentConfigService = documentConfigService;
    }

    /** A draft-started flow must still be writable after its terminal callback. */
    AiCrudConfig resolveStatusWriteConfig(AiBusinessFlowInstanceLink link,
                                          Map<String, Object> startVariables,
                                          AiCrudConfig preferred) {
        if (preferred != null && StringUtils.isNotBlank(preferred.getConfigKey())) {
            return preferred;
        }
        String snapshotConfigKey = startVariables == null ? null : textValue(startVariables.get("configKey"));
        String lookup = StringUtils.firstNonBlank(snapshotConfigKey, link == null ? null : link.getObjectCode());
        Long tenantId = link == null ? null : link.getTenantId();
        AiCrudConfig published = runtimeConfigResolver.published(tenantId, lookup);
        if (published != null) {
            return published;
        }
        String objectCode = link == null ? null : link.getObjectCode();
        if (StringUtils.isNotBlank(objectCode) && !StringUtils.equals(lookup, objectCode)) {
            published = runtimeConfigResolver.published(tenantId, objectCode);
            if (published != null) {
                return published;
            }
        }
        AiCrudConfig draft = runtimeConfigResolver.runtime(tenantId, lookup);
        if (draft != null) {
            return draft;
        }
        return runtimeConfigResolver.runtime(tenantId, objectCode);
    }

    void syncConfiguredStatusField(AiCrudConfig runtimeConfig,
                                   Long recordId,
                                   Map<String, Object> variables,
                                   String statusKey) {
        if (runtimeConfig == null || StringUtils.isBlank(runtimeConfig.getConfigKey()) || recordId == null) {
            return;
        }
        String statusField = configuredStatusField(variables);
        if (StringUtils.isBlank(statusField)) {
            return;
        }
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put(statusField, statusKey);
        // The callback transaction must fail before updating its link when this write fails.
        dynamicCrudService.updateInternalFieldsByIdAllowDraft(runtimeConfig.getConfigKey(), recordId, updateData);
    }

    String configuredStatusField(Map<String, Object> variables) {
        String statusField = firstNonBlankText(
                variables == null ? null : variables.get("flowStatusField"),
                variables == null ? null : variables.get("statusField"));
        if (StringUtils.isBlank(statusField)) {
            return "";
        }
        if (!Set.of("flowStatus", "flow_status").contains(statusField)) {
            throw new BusinessException("流程状态字段必须使用独立字段 flowStatus");
        }
        return statusField;
    }

    /** Unknown or terminal record states must not be overwritten by delayed task events. */
    String resolveCurrentDocumentStatusKey(AiBusinessFlowInstanceLink link,
                                           AiBusinessDocumentConfig documentConfig,
                                           AiCrudConfig statusRuntimeConfig,
                                           Map<String, Object> startVariables) {
        String statusField = configuredStatusField(startVariables);
        if (StringUtils.isNotBlank(statusField)) {
            return textValue(readRecordField(
                    statusRuntimeConfig == null ? null : statusRuntimeConfig.getConfigKey(),
                    link.getRecordId(), statusField));
        }
        if (documentConfig == null || StringUtils.isBlank(documentConfig.getStatusField())) {
            return null;
        }
        String storedValue = textValue(readRecordField(
                documentConfig.getConfigKey(), link.getRecordId(), documentConfig.getStatusField()));
        if (StringUtils.isBlank(storedValue)) {
            return null;
        }
        for (Map.Entry<String, String> entry : documentConfigService.toVO(documentConfig)
                .getStatusMapping().entrySet()) {
            if (storedValue.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Object readRecordField(String configKey, Long recordId, String field) {
        if (StringUtils.isAnyBlank(configKey, field) || recordId == null) {
            return null;
        }
        try {
            Map<String, Object> record = dynamicCrudService.selectByIdAllowDraft(configKey, recordId);
            return record == null ? null : record.get(field);
        } catch (Exception e) {
            log.debug("[低代码流程回调] 读取单据状态失败: configKey={}, recordId={}, field={}, error={}",
                    configKey, recordId, field, e.getMessage());
            return null;
        }
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text) ? null : text;
    }

    private String firstNonBlankText(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }
}
