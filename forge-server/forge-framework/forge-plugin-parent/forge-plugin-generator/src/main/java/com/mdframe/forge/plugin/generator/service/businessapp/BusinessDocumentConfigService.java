package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessDocumentConfigDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessDocumentNoRulePreviewDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessDocumentConfigMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentNoRulePreviewVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentNoRuleTokenVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.id.service.ISequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务对象单据配置服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessDocumentConfigService {

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time"
    );

    private final BusinessDocumentConfigMapper documentConfigMapper;
    private final BusinessBindingMapper bindingMapper;
    private final BusinessObjectService objectService;
    private final AiCrudConfigMapper crudConfigMapper;
    private final ObjectMapper objectMapper;
    private final ISequenceService sequenceService;

    private record DocumentNoCandidate(String fieldName, int score, int index) {
    }

    public BusinessDocumentConfigVO getConfig(Long objectId) {
        AiBusinessObject object = objectService.requireEntity(objectId);
        AiBusinessDocumentConfig config = documentConfigMapper.selectByObjectId(resolveTenantId(), objectId);
        if (config == null) {
            config = documentConfigMapper.selectByObjectCode(resolveTenantId(), object.getObjectCode());
        }
        if (config == null) {
            BusinessDocumentConfigVO vo = new BusinessDocumentConfigVO();
            vo.setObjectId(object.getId());
            vo.setSuiteCode(object.getSuiteCode());
            vo.setObjectCode(object.getObjectCode());
            vo.setConfigKey(object.getConfigKey());
            vo.setDocumentEnabled(false);
            vo.setDocumentName(object.getObjectName() + "单据");
            vo.setStatusMapping(defaultStatusMapping());
            vo.setStatusMappingRows(defaultStatusRows());
            // 未建单据配置时不要查 FLOW/APPROVAL 绑定；发布/设计器摘要用空主流程即可。
            vo.setMainFlowSummary(unconfiguredMainFlowSummary());
            return vo;
        }
        return toVO(config);
    }

    public List<BusinessDocumentNoRuleTokenVO> listNoRuleTokens() {
        return noRuleEngine().listTokens();
    }

    public BusinessDocumentNoRulePreviewVO previewNoRule(BusinessDocumentNoRulePreviewDTO dto) {
        return noRuleEngine().preview(dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(Long objectId, BusinessDocumentConfigDTO dto) {
        if (dto == null) {
            throw new BusinessException("单据配置不能为空");
        }
        AiBusinessObject object = objectService.requireEntity(objectId);
        boolean enabled = Boolean.TRUE.equals(dto.getDocumentEnabled());
        String documentNoRule = noRuleEngine().normalizeTemplate(StringUtils.firstNonBlank(dto.getNoRuleTemplate(), dto.getDocumentNoRule()));
        List<BusinessDocumentConfigVO.StatusMappingRowVO> statusRows = normalizeStatusRows(
                dto.getStatusMappingRows(), dto.getStatusMapping());
        Map<String, String> statusMapping = statusMappingFromRows(statusRows);
        if (StringUtils.isNotBlank(documentNoRule)) {
            noRuleEngine().validateTemplate(documentNoRule);
        }
        Long tenantId = resolveTenantId();
        String documentNoField = text(readObjectMap(dto.getOptions()).get("documentNoField"));
        if (enabled && StringUtils.isNotBlank(documentNoRule) && StringUtils.isBlank(documentNoField)) {
            AiCrudConfig runtimeConfig = crudConfigMapper.selectByConfigKey(tenantId, object.getConfigKey());
            documentNoField = inferDocumentNoField(runtimeConfig);
        }
        if (enabled) {
            validateRequiredField("单据状态字段", dto.getStatusField());
            validateObjectField(object, dto.getStatusField(), "单据状态字段");
            validateOptionalObjectField(object, dto.getStarterField(), "发起人字段");
            validateOptionalObjectField(object, dto.getOwnerField(), "负责人字段");
            if (StringUtils.isNotBlank(documentNoRule) && StringUtils.isBlank(documentNoField)) {
                throw new BusinessException("启用编号规则时必须配置单据编号字段");
            }
            validateOptionalObjectField(object, documentNoField, "单据编号字段");
        }

        AiBusinessDocumentConfig config = documentConfigMapper.selectByObjectId(tenantId, objectId);
        if (config == null) {
            config = documentConfigMapper.selectByObjectCode(tenantId, object.getObjectCode());
        }
        if (config == null) {
            config = new AiBusinessDocumentConfig();
            config.setTenantId(tenantId);
            config.setObjectId(object.getId());
            config.setSuiteCode(object.getSuiteCode());
            config.setObjectCode(object.getObjectCode());
        }
        config.setObjectId(object.getId());
        config.setSuiteCode(object.getSuiteCode());
        config.setObjectCode(object.getObjectCode());
        config.setConfigKey(object.getConfigKey());
        config.setDocumentEnabled(enabled ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        config.setDocumentName(StringUtils.defaultIfBlank(dto.getDocumentName(), object.getObjectName() + "单据"));
        config.setDocumentNoRule(StringUtils.trimToNull(documentNoRule));
        config.setStatusField(StringUtils.trimToNull(dto.getStatusField()));
        config.setStarterField(StringUtils.trimToNull(dto.getStarterField()));
        config.setOwnerField(StringUtils.trimToNull(dto.getOwnerField()));
        String requestedFlowKey = StringUtils.trimToNull(dto.getDefaultFlowKey());
        AiBusinessBinding mainFlowBinding = selectMainFlowBinding(resolveTenantId(), object.getObjectCode());
        config.setDefaultFlowKey(mainFlowBinding == null
                ? requestedFlowKey
                : StringUtils.defaultIfBlank(resolveFlowModelKey(readBindingConfig(mainFlowBinding.getBindingConfig())),
                        mainFlowBinding.getBindingKey()));
        config.setStatusMapping(writeJson(statusMapping, "单据状态映射"));
        config.setOptions(writeJson(buildOptions(dto, documentNoRule, documentNoField, statusRows), "单据扩展配置"));

        if (config.getId() == null) {
            documentConfigMapper.insert(config);
        } else {
            documentConfigMapper.updateById(config);
        }
        syncLegacyFlowBindingIfNeeded(object, requestedFlowKey, config.getDocumentName());
    }

    public AiBusinessDocumentConfig selectEnabledByObjectCode(String objectCode) {
        return selectEnabledByObjectCode(resolveTenantId(), objectCode);
    }

    public AiBusinessDocumentConfig selectEnabledByObjectCode(Long tenantId, String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return null;
        }
        AiBusinessDocumentConfig config = documentConfigMapper.selectByObjectCode(
                tenantId != null ? tenantId : resolveTenantId(), objectCode);
        if (config == null || !EnableStatus.ENABLED.matches(config.getDocumentEnabled())) {
            return null;
        }
        return config;
    }

    public AiBusinessDocumentConfig selectEnabledByConfigKey(Long tenantId, String configKey) {
        if (StringUtils.isBlank(configKey)) {
            return null;
        }
        AiBusinessDocumentConfig config = documentConfigMapper.selectByConfigKey(
                tenantId != null ? tenantId : resolveTenantId(), configKey);
        if (config == null || !EnableStatus.ENABLED.matches(config.getDocumentEnabled())) {
            return null;
        }
        return config;
    }

    public BusinessDocumentConfigVO toVO(AiBusinessDocumentConfig config) {
        return toVO(config, (AiCrudConfig) null);
    }

    public BusinessDocumentConfigVO toVO(AiBusinessDocumentConfig config, AiCrudConfig runtimeConfig) {
        Long tenantId = config.getTenantId() != null ? config.getTenantId() : resolveTenantId();
        Map<String, Object> mainFlowSummary = EnableStatus.ENABLED.matches(config.getDocumentEnabled())
                ? buildMainFlowSummary(tenantId, config.getObjectCode(), config.getDefaultFlowKey())
                : unconfiguredMainFlowSummary();
        return toVO(config, runtimeConfig, mainFlowSummary);
    }

    /**
     * 使用调用方已经解析的主流程绑定构建单据配置视图，避免发起链路重复查询绑定。
     */
    public BusinessDocumentConfigVO toVO(AiBusinessDocumentConfig config,
                                         AiCrudConfig runtimeConfig,
                                         AiBusinessBinding mainFlowBinding) {
        Map<String, Object> mainFlowSummary = buildMainFlowSummary(mainFlowBinding, config.getDefaultFlowKey());
        return toVO(config, runtimeConfig, mainFlowSummary);
    }

    private BusinessDocumentConfigVO toVO(AiBusinessDocumentConfig config,
                                          AiCrudConfig runtimeConfig,
                                          Map<String, Object> mainFlowSummary) {
        Map<String, Object> options = readObjectMap(config.getOptions());
        Long tenantId = config.getTenantId() != null ? config.getTenantId() : resolveTenantId();
        BusinessDocumentConfigVO vo = new BusinessDocumentConfigVO();
        vo.setId(config.getId());
        vo.setObjectId(config.getObjectId());
        vo.setSuiteCode(config.getSuiteCode());
        vo.setObjectCode(config.getObjectCode());
        vo.setConfigKey(config.getConfigKey());
        vo.setDocumentEnabled(EnableStatus.ENABLED.matches(config.getDocumentEnabled()));
        vo.setDocumentName(config.getDocumentName());
        String normalizedDocumentNoRule = noRuleEngine().normalizeTemplate(config.getDocumentNoRule());
        vo.setDocumentNoRule(normalizedDocumentNoRule);
        vo.setNoRuleTemplate(StringUtils.defaultIfBlank(noRuleEngine().normalizeTemplate(text(options.get("noRuleTemplate"))), normalizedDocumentNoRule));
        if (StringUtils.isNotBlank(vo.getNoRuleTemplate())) {
            BusinessDocumentNoRulePreviewDTO previewDTO = new BusinessDocumentNoRulePreviewDTO();
            previewDTO.setTemplate(vo.getNoRuleTemplate());
            previewDTO.setSuiteCode(config.getSuiteCode());
            previewDTO.setObjectCode(config.getObjectCode());
            vo.setNoRulePreview(previewNoRule(previewDTO));
        }
        vo.setStatusField(config.getStatusField());
        vo.setStarterField(config.getStarterField());
        vo.setOwnerField(config.getOwnerField());
        vo.setDefaultFlowKey(StringUtils.defaultIfBlank(text(mainFlowSummary.get("flowModelKey")), config.getDefaultFlowKey()));
        vo.setStatusMapping(readStringMap(config.getStatusMapping()));
        vo.setStatusMappingRows(readStatusRows(options, vo.getStatusMapping()));
        vo.setStatusActionPolicy(readObjectMap(options.get("statusActionPolicy")));
        vo.setMainFlowSummary(mainFlowSummary);
        String documentNoField = text(options.get("documentNoField"));
        if (StringUtils.isBlank(documentNoField)) {
            documentNoField = resolveDocumentNoField(config,
                    runtimeConfig != null ? runtimeConfig : selectRuntimeConfig(tenantId, config));
        }
        if (StringUtils.isNotBlank(documentNoField)) {
            options.put("documentNoField", documentNoField);
        }
        vo.setOptions(options);
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

    public String resolveDocumentNoField(AiBusinessDocumentConfig config, AiCrudConfig runtimeConfig) {
        if (config == null) {
            return null;
        }
        Map<String, Object> options = readObjectMap(config.getOptions());
        String configured = StringUtils.firstNonBlank(
                text(options.get("documentNoField")),
                text(options.get("documentNoFieldCode")),
                text(options.get("noField")));
        if (StringUtils.isNotBlank(configured)) {
            return configured.trim();
        }
        return inferDocumentNoField(runtimeConfig);
    }

    private AiCrudConfig selectRuntimeConfig(Long tenantId, AiBusinessDocumentConfig config) {
        if (config == null) {
            return null;
        }
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
        AiCrudConfig runtimeConfig = null;
        if (StringUtils.isNotBlank(config.getConfigKey())) {
            runtimeConfig = crudConfigMapper.selectByConfigKey(effectiveTenantId, config.getConfigKey());
        }
        if (runtimeConfig == null && StringUtils.isNotBlank(config.getObjectCode())) {
            runtimeConfig = crudConfigMapper.selectPublishedByObjectCode(effectiveTenantId, config.getObjectCode());
        }
        return runtimeConfig;
    }

    private String inferDocumentNoField(AiCrudConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return null;
        }
        DocumentNoCandidate best = null;
        int index = 0;
        if (StringUtils.isNotBlank(runtimeConfig.getModelSchema())) {
            try {
                LowcodeModelSchema modelSchema = objectMapper.readValue(runtimeConfig.getModelSchema(), LowcodeModelSchema.class);
                if (modelSchema.getFields() != null) {
                    for (LowcodeFieldSchema field : modelSchema.getFields()) {
                        if (field == null) {
                            continue;
                        }
                        best = chooseDocumentNoCandidate(best, field.getField(), field.getColumnName(), field.getLabel(), index++);
                    }
                }
            } catch (Exception ignored) {
                // Fall back to edit schema.
            }
        }
        if (StringUtils.isNotBlank(runtimeConfig.getEditSchema())) {
            try {
                List<Map<String, Object>> fields = objectMapper.readValue(
                        runtimeConfig.getEditSchema(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> field : fields) {
                    if (field == null) {
                        continue;
                    }
                    String fieldName = StringUtils.firstNonBlank(
                            text(field.get("field")),
                            text(field.get("prop")),
                            text(field.get("key")),
                            text(field.get("model"))
                    );
                    String columnName = StringUtils.firstNonBlank(text(field.get("columnName")), text(field.get("fieldCode")));
                    String label = StringUtils.firstNonBlank(text(field.get("label")), text(field.get("title")));
                    best = chooseDocumentNoCandidate(best, fieldName, columnName, label, index++);
                }
            } catch (Exception ignored) {
                // No inferred document number field.
            }
        }
        return best == null || best.score() < 75 ? null : best.fieldName();
    }

    private DocumentNoCandidate chooseDocumentNoCandidate(DocumentNoCandidate current,
                                                          String fieldName,
                                                          String columnName,
                                                          String label,
                                                          int index) {
        String resolvedField = StringUtils.firstNonBlank(fieldName, columnName);
        if (StringUtils.isBlank(resolvedField)) {
            return current;
        }
        int score = scoreDocumentNoCandidate(fieldName, columnName, label);
        if (score <= 0) {
            return current;
        }
        DocumentNoCandidate next = new DocumentNoCandidate(resolvedField.trim(), score, index);
        if (current == null || next.score() > current.score()
                || (next.score() == current.score() && next.index() < current.index())) {
            return next;
        }
        return current;
    }

    private int scoreDocumentNoCandidate(String fieldName, String columnName, String label) {
        int score = Math.max(scoreDocumentNoCode(fieldName), scoreDocumentNoCode(columnName));
        String labelText = StringUtils.defaultString(label);
        if (StringUtils.containsAny(labelText, "申请单号", "单据编号", "单据号", "业务单号")) {
            score = Math.max(score, 120);
        } else if (StringUtils.containsAny(labelText, "单号", "编号", "流水号")) {
            score = Math.max(score, 90);
        }
        return score;
    }

    private int scoreDocumentNoCode(String code) {
        String normalized = normalizeFieldKey(code);
        if (StringUtils.isBlank(normalized)) {
            return 0;
        }
        return switch (normalized) {
            case "documentno", "documentnumber" -> 120;
            case "applicationno", "applicationnumber", "applyno", "applynumber" -> 115;
            case "billno", "billnumber", "orderno", "ordernumber", "businessno", "businessnumber" -> 105;
            case "serialno", "serialnumber" -> 95;
            default -> normalized.endsWith("no") || normalized.endsWith("number") ? 60 : 0;
        };
    }

    private String normalizeFieldKey(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    public String generateDocumentNo(AiBusinessDocumentConfig config, Map<String, Object> recordData) {
        return noRuleEngine().generate(config, recordData);
    }

    public void syncDefaultFlowKeyByObjectCode(Long tenantId, String objectCode, String flowModelKey) {
        if (StringUtils.isBlank(objectCode)) {
            return;
        }
        AiBusinessDocumentConfig config = documentConfigMapper.selectByObjectCode(
                tenantId != null ? tenantId : resolveTenantId(), objectCode);
        if (config == null) {
            return;
        }
        config.setDefaultFlowKey(StringUtils.trimToNull(flowModelKey));
        documentConfigMapper.updateById(config);
    }

    private Map<String, Object> buildOptions(BusinessDocumentConfigDTO dto,
                                             String documentNoRule,
                                             String documentNoField,
                                             List<BusinessDocumentConfigVO.StatusMappingRowVO> statusRows) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (dto.getOptions() != null) {
            options.putAll(dto.getOptions());
        }
        options.put("noRuleTemplate", StringUtils.trimToNull(documentNoRule));
        options.put("documentNoField", StringUtils.trimToNull(documentNoField));
        options.put("statusMappingRows", statusRows);
        options.put("statusActionPolicy", dto.getStatusActionPolicy() == null
                ? new LinkedHashMap<>()
                : dto.getStatusActionPolicy());
        return options;
    }

    private List<BusinessDocumentConfigVO.StatusMappingRowVO> normalizeStatusRows(
            List<BusinessDocumentConfigDTO.StatusMappingRowDTO> rows,
            Map<String, String> legacyMapping) {
        Map<String, BusinessDocumentConfigVO.StatusMappingRowVO> defaults = new LinkedHashMap<>();
        for (BusinessDocumentConfigVO.StatusMappingRowVO row : defaultStatusRows()) {
            defaults.put(row.getStandardStatus(), row);
        }
        if (legacyMapping != null) {
            legacyMapping.forEach((key, value) -> {
                BusinessDocumentConfigVO.StatusMappingRowVO row = defaults.get(key);
                if (row != null && StringUtils.isNotBlank(value)) {
                    row.setStatusValue(value.trim());
                }
            });
        }
        if (rows != null) {
            for (BusinessDocumentConfigDTO.StatusMappingRowDTO input : rows) {
                if (input == null || StringUtils.isBlank(input.getStandardStatus())) {
                    continue;
                }
                String standardStatus = input.getStandardStatus().trim().toUpperCase();
                BusinessDocumentConfigVO.StatusMappingRowVO row = defaults.computeIfAbsent(
                        standardStatus, key -> statusRow(key, key, key, key, "default", true, true, false));
                if (StringUtils.isNotBlank(input.getStatusValue())) {
                    row.setStatusValue(input.getStatusValue().trim());
                }
                if (StringUtils.isNotBlank(input.getDisplayName())) {
                    row.setDisplayName(input.getDisplayName().trim());
                }
                if (StringUtils.isNotBlank(input.getTagType())) {
                    row.setTagType(input.getTagType().trim());
                }
                if (input.getAllowEdit() != null) {
                    row.setAllowEdit(input.getAllowEdit());
                }
                if (input.getAllowDelete() != null) {
                    row.setAllowDelete(input.getAllowDelete());
                }
                if (input.getAllowStartFlow() != null) {
                    row.setAllowStartFlow(input.getAllowStartFlow());
                }
            }
        }
        for (BusinessDocumentConfigVO.StatusMappingRowVO row : defaults.values()) {
            if (StringUtils.isBlank(row.getStatusValue())) {
                throw new BusinessException("状态映射缺少存储值: " + row.getStandardStatus());
            }
        }
        return new ArrayList<>(defaults.values());
    }

    private List<BusinessDocumentConfigVO.StatusMappingRowVO> readStatusRows(Map<String, Object> options,
                                                                            Map<String, String> legacyMapping) {
        Object rawRows = options == null ? null : options.get("statusMappingRows");
        if (rawRows != null) {
            try {
                List<BusinessDocumentConfigVO.StatusMappingRowVO> rows = objectMapper.convertValue(
                        rawRows, new TypeReference<List<BusinessDocumentConfigVO.StatusMappingRowVO>>() {});
                if (rows != null && !rows.isEmpty()) {
                    return normalizeStatusRowsFromVO(rows, legacyMapping);
                }
            } catch (Exception ignored) {
                // Fall back to legacy map.
            }
        }
        return normalizeStatusRows(null, legacyMapping);
    }

    private List<BusinessDocumentConfigVO.StatusMappingRowVO> normalizeStatusRowsFromVO(
            List<BusinessDocumentConfigVO.StatusMappingRowVO> rows,
            Map<String, String> legacyMapping) {
        List<BusinessDocumentConfigDTO.StatusMappingRowDTO> dtoRows = new ArrayList<>();
        for (BusinessDocumentConfigVO.StatusMappingRowVO row : rows) {
            BusinessDocumentConfigDTO.StatusMappingRowDTO dto = new BusinessDocumentConfigDTO.StatusMappingRowDTO();
            dto.setStandardStatus(row.getStandardStatus());
            dto.setStatusValue(row.getStatusValue());
            dto.setDisplayName(row.getDisplayName());
            dto.setTagType(row.getTagType());
            dto.setAllowEdit(row.getAllowEdit());
            dto.setAllowDelete(row.getAllowDelete());
            dto.setAllowStartFlow(row.getAllowStartFlow());
            dtoRows.add(dto);
        }
        return normalizeStatusRows(dtoRows, legacyMapping);
    }

    private Map<String, String> statusMappingFromRows(List<BusinessDocumentConfigVO.StatusMappingRowVO> rows) {
        Map<String, String> result = new LinkedHashMap<>();
        for (BusinessDocumentConfigVO.StatusMappingRowVO row : rows) {
            if (StringUtils.isNotBlank(row.getStandardStatus()) && StringUtils.isNotBlank(row.getStatusValue())) {
                result.put(row.getStandardStatus(), row.getStatusValue());
            }
        }
        return normalizeStatusMapping(result);
    }

    private List<BusinessDocumentConfigVO.StatusMappingRowVO> defaultStatusRows() {
        List<BusinessDocumentConfigVO.StatusMappingRowVO> rows = new ArrayList<>();
        rows.add(statusRow("DRAFT", "草稿", "DRAFT", "草稿", "default", true, true, true));
        rows.add(statusRow("SUBMITTED", "已提交", "SUBMITTED", "已提交", "info", false, false, false));
        rows.add(statusRow("IN_PROCESS", "流程中", "IN_PROCESS", "流程中", "warning", false, false, false));
        // 待修改允许编辑但不允许另起新流程：发起人应在原流程实例上重提，避免审批轨迹断裂。
        rows.add(statusRow("NEED_MODIFY", "待修改", "NEED_MODIFY", "待修改", "warning", true, false, false));
        rows.add(statusRow("APPROVED", "已通过", "APPROVED", "已通过", "success", false, false, false));
        rows.add(statusRow("REJECTED", "已驳回", "REJECTED", "已驳回", "error", true, false, false));
        rows.add(statusRow("CANCELED", "已撤回", "CANCELED", "已撤回", "default", true, false, false));
        rows.add(statusRow("CLOSED", "已关闭", "CLOSED", "已关闭", "default", false, false, false));
        return rows;
    }

    private BusinessDocumentConfigVO.StatusMappingRowVO statusRow(String standardStatus,
                                                                  String standardLabel,
                                                                  String statusValue,
                                                                  String displayName,
                                                                  String tagType,
                                                                  Boolean allowEdit,
                                                                  Boolean allowDelete,
                                                                  Boolean allowStartFlow) {
        BusinessDocumentConfigVO.StatusMappingRowVO row = new BusinessDocumentConfigVO.StatusMappingRowVO();
        row.setStandardStatus(standardStatus);
        row.setStandardLabel(standardLabel);
        row.setStatusValue(statusValue);
        row.setDisplayName(displayName);
        row.setTagType(tagType);
        row.setAllowEdit(allowEdit);
        row.setAllowDelete(allowDelete);
        row.setAllowStartFlow(allowStartFlow);
        return row;
    }

    private Map<String, Object> unconfiguredMainFlowSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("configured", false);
        summary.put("complete", false);
        summary.put("gaps", List.of("未配置主流程"));
        summary.put("compatibilitySource", "NONE");
        return summary;
    }

    private Map<String, Object> buildMainFlowSummary(Long tenantId, String objectCode, String legacyDefaultFlowKey) {
        return buildMainFlowSummary(selectMainFlowBinding(tenantId, objectCode), legacyDefaultFlowKey);
    }

    private Map<String, Object> buildMainFlowSummary(AiBusinessBinding binding, String legacyDefaultFlowKey) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (binding != null) {
            Map<String, Object> config = readBindingConfig(binding.getBindingConfig());
            String flowModelKey = StringUtils.defaultIfBlank(resolveFlowModelKey(config), binding.getBindingKey());
            String startMode = normalizeStartMode(text(config.get("startMode")));
            List<?> variableMapping = config.get("variableMapping") instanceof List<?> list ? list : List.of();
            List<String> gaps = new ArrayList<>();
            if (StringUtils.isBlank(flowModelKey)) {
                gaps.add("未配置主流程");
            }
            if (StringUtils.isBlank(startMode)) {
                gaps.add("发起方式未配置");
            }
            if (variableMapping.isEmpty()) {
                gaps.add("变量映射缺失");
            }
            if (EnableStatus.DISABLED.matches(binding.getStatus())) {
                gaps.add("主流程绑定已停用");
            }
            summary.put("configured", StringUtils.isNotBlank(flowModelKey));
            summary.put("bindingId", binding.getId());
            summary.put("bindingType", binding.getBindingType());
            summary.put("bindingStatus", binding.getStatus());
            summary.put("flowModelKey", flowModelKey);
            summary.put("flowModelName", StringUtils.defaultIfBlank(text(config.get("flowModelName")), binding.getBindingName()));
            summary.put("startMode", startMode);
            summary.put("variableMappingCount", variableMapping.size());
            summary.put("complete", gaps.isEmpty());
            summary.put("gaps", gaps);
            summary.put("compatibilitySource", "APPROVAL".equalsIgnoreCase(binding.getBindingType())
                    ? "LEGACY_APPROVAL_BINDING"
                    : "AI_BUSINESS_BINDING");
            return summary;
        }
        if (StringUtils.isNotBlank(legacyDefaultFlowKey)) {
            summary.put("configured", true);
            summary.put("flowModelKey", legacyDefaultFlowKey);
            summary.put("flowModelName", legacyDefaultFlowKey);
            summary.put("startMode", "MANUAL");
            summary.put("variableMappingCount", 0);
            summary.put("complete", false);
            summary.put("gaps", List.of("历史默认流程缺少变量映射，请在流程与自动化中保存一次主流程"));
            summary.put("compatibilitySource", "DOCUMENT_DEFAULT_FLOW");
            return summary;
        }
        summary.put("configured", false);
        summary.put("complete", false);
        summary.put("gaps", List.of("未配置主流程"));
        summary.put("compatibilitySource", "NONE");
        return summary;
    }

    private AiBusinessBinding selectMainFlowBinding(Long tenantId, String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return null;
        }
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
        AiBusinessBinding flowBinding = bindingMapper.selectBindingByTypeAndCode(
                effectiveTenantId, "OBJECT", objectCode, "FLOW");
        if (isBindingEnabled(flowBinding)) {
            return flowBinding;
        }
        AiBusinessBinding legacyApprovalBinding = bindingMapper.selectBindingByTypeAndCode(
                effectiveTenantId, "OBJECT", objectCode, "APPROVAL");
        if (isBindingEnabled(legacyApprovalBinding)) {
            return legacyApprovalBinding;
        }
        return flowBinding != null ? flowBinding : legacyApprovalBinding;
    }

    private boolean isBindingEnabled(AiBusinessBinding binding) {
        return binding != null && !EnableStatus.DISABLED.matches(binding.getStatus());
    }

    private String normalizeStartMode(String startMode) {
        String normalized = StringUtils.defaultIfBlank(startMode, "MANUAL").trim().toUpperCase();
        if ("MANUAL_AND_TRIGGER".equals(normalized) || "MANUAL_TRIGGER".equals(normalized) || "BOTH".equals(normalized)) {
            return "BOTH";
        }
        if ("AUTO".equals(normalized) || "AUTOMATIC".equals(normalized)) {
            return "TRIGGER";
        }
        if ("TRIGGER".equals(normalized)) {
            return "TRIGGER";
        }
        return "MANUAL";
    }

    private void syncLegacyFlowBindingIfNeeded(AiBusinessObject object, String requestedFlowKey, String documentName) {
        if (object == null || StringUtils.isBlank(object.getObjectCode()) || StringUtils.isBlank(requestedFlowKey)) {
            return;
        }
        Long tenantId = resolveTenantId();
        AiBusinessBinding existing = selectMainFlowBinding(tenantId, object.getObjectCode());
        if (existing != null) {
            return;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("flowModelKey", requestedFlowKey);
        config.put("flowModelName", requestedFlowKey);
        config.put("titleTemplate", StringUtils.defaultIfBlank(documentName, object.getObjectName()) + "-${id}");
        config.put("startMode", "MANUAL");
        config.put("variableMapping", new ArrayList<>());
        config.put("conditionFlows", new ArrayList<>());
        config.put("options", Map.of("compatibilitySource", "DOCUMENT_DEFAULT_FLOW"));

        AiBusinessBinding binding = new AiBusinessBinding();
        binding.setTenantId(tenantId);
        binding.setTargetType("OBJECT");
        binding.setTargetId(object.getId());
        binding.setTargetCode(object.getObjectCode());
        binding.setBindingType("FLOW");
        binding.setBindingKey(requestedFlowKey);
        binding.setBindingName(requestedFlowKey + " 流程");
        binding.setBindingConfig(writeJson(config, "流程绑定兼容配置"));
        binding.setStatus(EnableStatus.ENABLED.getCode());
        binding.setSortOrder(0);
        bindingMapper.insert(binding);
    }

    private Map<String, Object> readBindingConfig(String bindingConfig) {
        if (StringUtils.isBlank(bindingConfig)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(bindingConfig, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String resolveFlowModelKey(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                text(config.get("flowModelKey")),
                text(config.get("flowKey")),
                text(config.get("processDefinitionKey")),
                text(config.get("modelKey"))
        );
    }

    private void validateRequiredField(String label, String field) {
        if (StringUtils.isBlank(field)) {
            throw new BusinessException(label + "不能为空");
        }
    }

    private void validateOptionalObjectField(AiBusinessObject object, String field, String label) {
        if (StringUtils.isBlank(field)) {
            return;
        }
        validateObjectField(object, field, label);
    }

    private void validateObjectField(AiBusinessObject object, String field, String label) {
        Set<String> fields = collectObjectFields(object);
        if (!fields.contains(field)) {
            throw new BusinessException(label + "不存在: " + field);
        }
    }

    private Set<String> collectObjectFields(AiBusinessObject object) {
        Set<String> fields = new LinkedHashSet<>(SYSTEM_FIELDS);
        if (object == null || StringUtils.isBlank(object.getConfigKey())) {
            return fields;
        }
        AiCrudConfig config = crudConfigMapper.selectByConfigKey(resolveTenantId(), object.getConfigKey());
        if (config == null || StringUtils.isBlank(config.getModelSchema())) {
            return fields;
        }
        try {
            LowcodeModelSchema modelSchema = objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
            if (modelSchema.getFields() == null) {
                return fields;
            }
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field == null) {
                    continue;
                }
                addFieldAlias(fields, field.getField());
                addFieldAlias(fields, field.getColumnName());
            }
        } catch (Exception e) {
            throw new BusinessException("读取业务对象字段失败: " + e.getMessage());
        }
        return fields;
    }

    private void addFieldAlias(Set<String> fields, String field) {
        if (StringUtils.isBlank(field)) {
            return;
        }
        fields.add(field);
        fields.add(snakeToCamel(field));
    }

    private Map<String, String> normalizeStatusMapping(Map<String, String> input) {
        Map<String, String> result = defaultStatusMapping();
        if (input == null) {
            return result;
        }
        input.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                result.put(key.trim(), value.trim());
            }
        });
        return result;
    }

    private Map<String, String> defaultStatusMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("DRAFT", "DRAFT");
        mapping.put("SUBMITTED", "SUBMITTED");
        mapping.put("IN_PROCESS", "IN_PROCESS");
        mapping.put("NEED_MODIFY", "NEED_MODIFY");
        mapping.put("APPROVED", "APPROVED");
        mapping.put("REJECTED", "REJECTED");
        mapping.put("CANCELED", "CANCELED");
        mapping.put("CLOSED", "CLOSED");
        return mapping;
    }

    private Map<String, String> readStringMap(String json) {
        if (StringUtils.isBlank(json)) {
            return defaultStatusMapping();
        }
        try {
            Map<String, String> value = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            return normalizeStatusMapping(value);
        } catch (Exception e) {
            return defaultStatusMapping();
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> readObjectMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> {
                if (key != null) {
                    result.put(String.valueOf(key), itemValue);
                }
            });
            return result;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(label + "格式不正确");
        }
    }

    private String snakeToCamel(String value) {
        if (StringUtils.isBlank(value) || !value.contains("_")) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BusinessDocumentNoRuleEngine noRuleEngine() {
        return new BusinessDocumentNoRuleEngine(sequenceService);
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId != null ? tenantId : 1L;
    }
}
