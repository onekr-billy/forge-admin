package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowBindingDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.normalizeBusinessBindingMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.toBusinessBindingDTO;

/** Applies document and low-code binding status transitions to business records. */
@Slf4j
final class BusinessFlowStatusTransitionService {

    private final BusinessDocumentConfigService documentConfigService;
    private final DynamicCrudService dynamicCrudService;

    BusinessFlowStatusTransitionService(BusinessDocumentConfigService documentConfigService,
                                        DynamicCrudService dynamicCrudService) {
        this.documentConfigService = documentConfigService;
        this.dynamicCrudService = dynamicCrudService;
    }

    void updateBusinessFlowStatus(AiBusinessDocumentConfig documentConfig,
                                  AiCrudConfig runtimeConfig,
                                  JSONObject bindingConfig,
                                  Long recordId,
                                  String statusKey) {
        if (documentConfig != null) {
            updateDocumentStatus(documentConfig, recordId, statusKey);
            return;
        }
        BusinessFlowBindingDTO.BusinessBindingDTO businessBinding = toBusinessBindingDTO(
                bindingConfig == null ? null : bindingConfig.getJSONObject("businessBinding"));
        if (businessBinding == null || StringUtils.isBlank(businessBinding.getStatusField())) {
            return;
        }
        String mode = normalizeBusinessBindingMode(businessBinding.getMode());
        if ("ADAPTER".equals(mode)) {
            log.debug("[低代码流程状态] Adapter 模式跳过平台直接回写: recordId={}, status={}", recordId, statusKey);
            return;
        }
        if (runtimeConfig == null || StringUtils.isBlank(runtimeConfig.getConfigKey())) {
            throw new BusinessException("业务表绑定缺少低代码运行配置，无法更新流程状态");
        }
        validateBusinessBindingRuntimeTable(businessBinding, runtimeConfig);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put(businessBinding.getStatusField(), resolveBusinessBindingStatusValue(bindingConfig, statusKey));
        dynamicCrudService.updateInternalFieldsById(runtimeConfig.getConfigKey(), recordId, updateData);
    }

    private void updateDocumentStatus(AiBusinessDocumentConfig config, Long recordId, String statusKey) {
        if (StringUtils.isBlank(config.getStatusField())) {
            throw new BusinessException("单据状态字段未配置");
        }
        if (StringUtils.isBlank(config.getConfigKey())) {
            throw new BusinessException("单据缺少动态运行配置，无法更新状态");
        }
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put(config.getStatusField(), resolveDocumentStatusValue(config, statusKey));
        dynamicCrudService.updateInternalFieldsById(config.getConfigKey(), recordId, updateData);
    }

    private String resolveBusinessBindingStatusValue(JSONObject bindingConfig, String statusKey) {
        JSONObject document = bindingConfig == null ? null : bindingConfig.getJSONObject("document");
        JSONObject statusMapping = document == null ? null : document.getJSONObject("statusMapping");
        return statusMapping == null
                ? statusKey
                : StringUtils.defaultIfBlank(statusMapping.getString(statusKey), statusKey);
    }

    private void validateBusinessBindingRuntimeTable(BusinessFlowBindingDTO.BusinessBindingDTO businessBinding,
                                                     AiCrudConfig runtimeConfig) {
        String bindingTable = StringUtils.trimToNull(businessBinding.getTableName());
        if (bindingTable == null) {
            return;
        }
        String runtimeTable = StringUtils.firstNonBlank(
                runtimeConfig.getRuntimeTableName(), runtimeConfig.getTableName());
        if (StringUtils.isNotBlank(runtimeTable) && !bindingTable.equalsIgnoreCase(runtimeTable)) {
            throw new BusinessException("业务表绑定与发布运行表不一致，禁止直接回写状态");
        }
    }

    private String resolveDocumentStatusValue(AiBusinessDocumentConfig config, String statusKey) {
        Map<String, String> statusMapping = documentConfigService.toVO(config).getStatusMapping();
        return StringUtils.defaultIfBlank(statusMapping.get(statusKey), statusKey);
    }
}
