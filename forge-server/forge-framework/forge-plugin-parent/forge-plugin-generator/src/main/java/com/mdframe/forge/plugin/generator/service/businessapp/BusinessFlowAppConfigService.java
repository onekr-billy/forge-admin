package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowAppConfigDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowBindingDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessDocumentConfigDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowAppConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowBindingVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务流程应用统一配置服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessFlowAppConfigService {

    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessFlowService flowService;

    public BusinessFlowAppConfigVO getConfig(String objectCode) {
        String code = normalizeObjectCode(objectCode);
        BusinessObjectVO object = findObject(code);
        if (object == null) {
            return buildCodeAppConfig(code);
        }
        return buildConfig(object);
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowAppConfigVO saveConfig(String objectCode, BusinessFlowAppConfigDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务流程应用配置不能为空");
        }
        String code = normalizeObjectCode(objectCode);
        BusinessObjectVO object = findObject(code);
        if (object == null) {
            return saveCodeAppConfig(code, dto);
        }
        boolean codeAppObject = isCodeAppObject(object);
        if (codeAppObject && isDocumentEnabled(dto.getDocumentConfig())) {
            throw new BusinessException("代码应用不能在低代码单据规则中启用单据模式，请通过业务代码或流程绑定维护状态回写");
        }
        if (!codeAppObject && dto.getDocumentConfig() != null) {
            documentConfigService.saveConfig(object.getId(), dto.getDocumentConfig());
        }
        if (dto.getFlowBinding() != null) {
            if (codeAppObject) {
                mergeCodeAppMetadata(dto);
                normalizeCodeAppBinding(dto.getFlowBinding());
            }
            flowService.saveFlowBinding(object.getObjectCode(), dto.getFlowBinding());
        } else if (codeAppObject && hasCodeAppMetadata(dto)) {
            flowService.saveCodeAppMetadata(object.getObjectCode(), dto.getOptions().get("codeAppMetadata"));
        }
        return buildConfig(object);
    }

    private BusinessFlowAppConfigVO saveCodeAppConfig(String objectCode, BusinessFlowAppConfigDTO dto) {
        Map<String, Object> formAssets = flowService.getFormAssets(objectCode);
        if (!hasFormAssets(formAssets)) {
            throw new BusinessException("业务对象不存在或未注册代码表单资产: " + objectCode);
        }
        if (isDocumentEnabled(dto.getDocumentConfig())) {
            throw new BusinessException("代码应用不能在低代码单据规则中启用单据模式，请通过业务代码或流程绑定维护状态回写");
        }
        if (dto.getFlowBinding() != null) {
            mergeCodeAppMetadata(dto);
            normalizeCodeAppBinding(dto.getFlowBinding());
            flowService.saveFlowBinding(objectCode, dto.getFlowBinding());
        } else if (hasCodeAppMetadata(dto)) {
            if (!flowService.saveCodeAppMetadata(objectCode, dto.getOptions().get("codeAppMetadata"))) {
                throw new BusinessException("请先为代码应用选择流程模型后再保存字段与视图配置");
            }
        }
        return buildCodeAppConfig(objectCode);
    }

    private BusinessFlowAppConfigVO buildConfig(BusinessObjectVO object) {
        BusinessDocumentConfigVO documentConfig = documentConfigService.getConfig(object.getId());
        BusinessFlowBindingVO flowBinding = flowService.getFlowBinding(object.getObjectCode());
        Map<String, Object> formAssets = flowService.getFormAssets(object.getObjectCode());

        BusinessFlowAppConfigVO vo = new BusinessFlowAppConfigVO();
        vo.setObjectId(object.getId());
        vo.setSuiteCode(object.getSuiteCode());
        vo.setObjectCode(object.getObjectCode());
        vo.setObjectName(object.getObjectName());
        vo.setConfigKey(StringUtils.firstNonBlank(object.getConfigKey(), documentConfig.getConfigKey()));
        vo.setDocumentConfig(documentConfig);
        vo.setFlowBinding(flowBinding);
        vo.setFormAssets(formAssets);
        vo.setSummary(buildSummary(documentConfig, flowBinding, formAssets));
        if (isCodeAppObject(object)) {
            Map<String, Object> codeAppMetadata = flowService.getCodeAppMetadata(object.getObjectCode());
            vo.getSummary().put("codeApp", true);
            vo.getSummary().put("documentManaged", false);
            vo.getSummary().put("metadataConfigured", !codeAppMetadata.isEmpty());
            vo.getOptions().put("codeApp", true);
            vo.getOptions().put("documentManaged", false);
            vo.getOptions().put("codeAppMetadata", codeAppMetadata);
            vo.getOptions().put("documentMessage", "代码应用由业务模块维护数据读写和状态流转；表单、列表和详情展示在应用设计器左侧对应面板配置，节点表单和字段权限在流程设计器中配置。");
        }
        return vo;
    }

    private BusinessFlowAppConfigVO buildCodeAppConfig(String objectCode) {
        Map<String, Object> formAssets = flowService.getFormAssets(objectCode);
        if (!hasFormAssets(formAssets)) {
            throw new BusinessException("业务对象不存在或未注册代码表单资产: " + objectCode);
        }
        BusinessFlowBindingVO flowBinding = flowService.getFlowBinding(objectCode);
        Map<String, Object> summary = buildSummary(null, flowBinding, formAssets);
        Map<String, Object> codeAppMetadata = flowService.getCodeAppMetadata(objectCode);
        summary.put("codeApp", true);
        summary.put("documentManaged", false);
        summary.put("metadataConfigured", !codeAppMetadata.isEmpty());

        BusinessFlowAppConfigVO vo = new BusinessFlowAppConfigVO();
        vo.setObjectCode(objectCode);
        vo.setObjectName(resolveCodeAppName(objectCode, formAssets));
        vo.setFlowBinding(flowBinding);
        vo.setFormAssets(formAssets);
        vo.setSummary(summary);
        vo.getOptions().put("codeApp", true);
        vo.getOptions().put("documentManaged", false);
        vo.getOptions().put("codeAppMetadata", codeAppMetadata);
        vo.getOptions().put("documentMessage", "代码应用由业务模块维护数据读写和状态流转；表单、列表和详情展示在应用设计器左侧对应面板配置，节点表单和字段权限在流程设计器中配置。");
        return vo;
    }

    @SuppressWarnings("unchecked")
    private void mergeCodeAppMetadata(BusinessFlowAppConfigDTO dto) {
        Map<String, Object> options = dto.getOptions() == null ? new LinkedHashMap<>() : dto.getOptions();
        Object metadata = options.get("codeAppMetadata");
        if (!(metadata instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> bindingOptions = dto.getFlowBinding().getOptions() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(dto.getFlowBinding().getOptions());
        bindingOptions.put("codeAppMetadata", new LinkedHashMap<>((Map<String, Object>) metadata));
        dto.getFlowBinding().setOptions(bindingOptions);
    }

    private Map<String, Object> buildSummary(BusinessDocumentConfigVO documentConfig,
                                             BusinessFlowBindingVO flowBinding,
                                             Map<String, Object> formAssets) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> mainFlow = documentConfig == null
                ? new LinkedHashMap<>()
                : documentConfig.getMainFlowSummary();
        boolean bindingConfigured = flowBinding != null && StringUtils.isNotBlank(flowBinding.getFlowModelKey());
        summary.put("documentEnabled", documentConfig != null && Boolean.TRUE.equals(documentConfig.getDocumentEnabled()));
        summary.put("statusField", documentConfig == null ? null : documentConfig.getStatusField());
        summary.put("flowConfigured", bindingConfigured || (mainFlow != null && Boolean.TRUE.equals(mainFlow.get("configured"))));
        summary.put("flowComplete", Boolean.TRUE.equals(flowBinding == null ? null : flowBinding.getComplete())
                || (mainFlow != null && Boolean.TRUE.equals(mainFlow.get("complete"))));
        summary.put("flowModelKey", flowBinding == null ? null : flowBinding.getFlowModelKey());
        summary.put("startMode", flowBinding == null ? null : flowBinding.getStartMode());
        summary.put("nodeFormCount", flowBinding == null || flowBinding.getNodeForms() == null
                ? 0
                : flowBinding.getNodeForms().size());
        Object assets = formAssets == null ? null : formAssets.get("formAssets");
        summary.put("formAssetCount", assets instanceof List<?> list ? list.size() : 0);
        summary.put("warnings", formAssets == null ? List.of() : formAssets.getOrDefault("warnings", List.of()));
        return summary;
    }

    private String normalizeObjectCode(String objectCode) {
        String code = StringUtils.trimToNull(objectCode);
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("业务对象编码不能为空");
        }
        return code;
    }

    private BusinessObjectVO findObject(String objectCode) {
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setObjectCode(objectCode);
        List<BusinessObjectVO> objects = businessObjectMapper.selectObjectList(resolveTenantId(), query);
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        return objects.get(0);
    }

    private boolean hasFormAssets(Map<String, Object> formAssets) {
        Object assets = formAssets == null ? null : formAssets.get("formAssets");
        return assets instanceof List<?> list && !list.isEmpty();
    }

    private boolean isCodeAppObject(BusinessObjectVO object) {
        if (object == null) {
            return false;
        }
        String options = compactJsonText(object.getOptions());
        String designerOptions = compactJsonText(object.getDesignerOptions());
        return options.contains("\"codeApp\":true") || designerOptions.contains("\"codeApp\":true");
    }

    private String compactJsonText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String resolveCodeAppName(String objectCode, Map<String, Object> formAssets) {
        Object objectName = formAssets == null ? null : formAssets.get("objectName");
        if (objectName != null && StringUtils.isNotBlank(String.valueOf(objectName))) {
            return String.valueOf(objectName);
        }
        Object assets = formAssets == null ? null : formAssets.get("formAssets");
        if (assets instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> asset) {
            String name = firstText(asset.get("businessName"), asset.get("appName"), asset.get("objectName"), asset.get("formName"));
            if (StringUtils.isNotBlank(name)) {
                return name;
            }
        }
        return objectCode;
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean isDocumentEnabled(BusinessDocumentConfigDTO documentConfig) {
        return documentConfig != null && Boolean.TRUE.equals(documentConfig.getDocumentEnabled());
    }

    private boolean hasCodeAppMetadata(BusinessFlowAppConfigDTO dto) {
        return dto != null
                && dto.getOptions() != null
                && dto.getOptions().get("codeAppMetadata") instanceof Map<?, ?>;
    }

    private void normalizeCodeAppBinding(BusinessFlowBindingDTO binding) {
        BusinessFlowBindingDTO.BusinessBindingDTO businessBinding = binding.getBusinessBinding();
        if (businessBinding == null) {
            businessBinding = new BusinessFlowBindingDTO.BusinessBindingDTO();
            binding.setBusinessBinding(businessBinding);
        }
        String mode = StringUtils.trimToEmpty(businessBinding.getMode()).toUpperCase();
        if (StringUtils.isBlank(mode) || "LOWCODE_OBJECT".equals(mode)) {
            businessBinding.setMode("ADAPTER");
        }
        if (StringUtils.isBlank(businessBinding.getPrimaryKeyField())) {
            businessBinding.setPrimaryKeyField("id");
        }
        if (StringUtils.isBlank(businessBinding.getTenantField())) {
            businessBinding.setTenantField("tenant_id");
        }
    }

    private Long resolveTenantId() {
        LoginUser user = SessionHelper.getLoginUser();
        return user != null && user.getTenantId() != null ? user.getTenantId() : 1L;
    }
}
