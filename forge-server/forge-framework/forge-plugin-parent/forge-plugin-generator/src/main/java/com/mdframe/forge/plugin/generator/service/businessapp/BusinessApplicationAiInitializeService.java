package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAppDraftDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDataModelDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeProcessSuggestionDTO;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectDesignerService.DesignerContext;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAiInitializeResultVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/** 将用户确认后的 AI 结构化方案写入业务应用设计态。 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationAiInitializeService {

    private static final int MAX_OBJECTS = 6;
    private static final int MAX_PROCESSES = 3;

    private final ObjectMapper objectMapper;
    private final BusinessApplicationService applicationService;
    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessObjectCreateService objectCreateService;
    private final BusinessObjectDesignerService designerService;
    private final BusinessNamingService namingService;
    private final BusinessProcessService businessProcessService;

    @Transactional(rollbackFor = Exception.class)
    public BusinessApplicationAiInitializeResultVO initialize(
            Long applicationId, LowcodeAiAppGenerateResult plan) {
        AiBusinessApplication application = applicationService.requireEntity(applicationId);
        if (!applicationObjectService.list(applicationId).isEmpty()) {
            throw new BusinessException("当前应用已经包含数据对象，不能重复套用 AI 方案");
        }
        List<LowcodeDataModelDTO> models = resolveModels(plan);
        List<LowcodeAppDraftDTO> apps = resolveApps(plan);
        if (models.isEmpty()) {
            throw new BusinessException("AI 方案缺少数据模型");
        }
        if (models.size() > MAX_OBJECTS) {
            throw new BusinessException("AI 方案最多支持 " + MAX_OBJECTS + " 个数据对象");
        }

        List<BusinessApplicationObjectDTO> bindings = new ArrayList<>();
        Map<String, Long> generatedObjectIds = new LinkedHashMap<>();
        Long primaryObjectId = null;
        String primaryObjectCode = null;
        for (int index = 0; index < models.size(); index++) {
            LowcodeDataModelDTO model = models.get(index);
            validateModel(model, index);
            LowcodeModelSchema modelSchema = copy(model.getModelSchema(), LowcodeModelSchema.class);
            LowcodePageSchema pageSchema = resolvePageSchema(model, apps, plan);
            String objectName = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(model.getModelName()),
                    modelSchema.getObject() == null ? null : StringUtils.trimToNull(modelSchema.getObject().getName()),
                    "AI 数据对象 " + (index + 1));
            String rawCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(model.getModelCode()),
                    modelSchema.getObject() == null ? null : StringUtils.trimToNull(modelSchema.getObject().getCode()),
                    namingService.generateObjectCode(objectName));
            String objectCode = namingService.normalizeObjectCode(
                    application.getApplicationCode() + "_" + rawCode, objectName);
            String modelCode = namingService.buildModelCode(application.getSuiteCode(), objectCode);

            BusinessObjectDTO object = new BusinessObjectDTO();
            object.setSuiteCode(application.getSuiteCode());
            object.setObjectName(objectName);
            object.setObjectCode(objectCode);
            object.setModelCode(modelCode);
            object.setObjectType(index == 0 ? "MASTER" : "LOOKUP");
            object.setCreateMode("AI_GENERATE");
            object.setDisplayField(resolveDisplayField(modelSchema));
            object.setDescription(StringUtils.defaultIfBlank(
                    model.getModelDesc(), "由 AI 应用创建向导生成的设计草稿"));
            object.setOptions(JSON.toJSONString(Map.of(
                    "createMode", "AI_GENERATE",
                    "requirementSummary", StringUtils.defaultString(plan.getRequirementSummary()))));
            object.setStatus(EnableStatus.ENABLED.getCode());
            Long objectId = objectCreateService.create(object);
            generatedObjectIds.put(rawCode, objectId);
            generatedObjectIds.put(objectCode, objectId);

            DesignerContext context = designerService.loadContext(objectId);
            context.setModelSchema(modelSchema);
            context.setPageSchema(pageSchema);
            designerService.saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());

            BusinessApplicationObjectDTO binding = new BusinessApplicationObjectDTO();
            binding.setObjectId(objectId);
            binding.setObjectRole(index == 0
                    ? BusinessApplicationObjectRole.PRIMARY : BusinessApplicationObjectRole.SHARED);
            binding.setSortOrder(index);
            binding.setOptions(JSON.toJSONString(Map.of("source", "AI_GENERATE")));
            bindings.add(binding);
            if (index == 0) {
                primaryObjectId = objectId;
                primaryObjectCode = objectCode;
            }
        }
        applicationObjectService.replace(applicationId, bindings);
        List<BusinessProcessVO> processes = createProcessDrafts(
                applicationId, plan.getProcessSuggestions(), generatedObjectIds, primaryObjectId);

        BusinessApplicationAiInitializeResultVO result = new BusinessApplicationAiInitializeResultVO();
        result.setApplicationId(applicationId);
        result.setPrimaryObjectId(primaryObjectId);
        result.setPrimaryObjectCode(primaryObjectCode);
        result.setObjects(applicationObjectService.list(applicationId));
        result.setProcesses(processes);
        return result;
    }

    private List<BusinessProcessVO> createProcessDrafts(
            Long applicationId,
            List<LowcodeProcessSuggestionDTO> suggestions,
            Map<String, Long> generatedObjectIds,
            Long primaryObjectId) {
        List<LowcodeProcessSuggestionDTO> values = suggestions == null
                ? List.of() : suggestions.stream().filter(item -> item != null).toList();
        if (values.size() > MAX_PROCESSES) {
            throw new BusinessException("AI 方案最多支持 " + MAX_PROCESSES + " 个业务流程建议");
        }
        List<BusinessProcessVO> processes = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            LowcodeProcessSuggestionDTO suggestion = values.get(index);
            String subjectCode = StringUtils.trimToNull(suggestion.getSubjectObjectCode());
            Long subjectObjectId = subjectCode == null
                    ? primaryObjectId : generatedObjectIds.get(subjectCode);
            if (subjectObjectId == null) {
                throw new BusinessException("AI 流程建议引用了未生成的数据对象: " + subjectCode);
            }
            String processName = StringUtils.trimToNull(suggestion.getProcessName());
            if (processName == null) {
                throw new BusinessException("AI 方案中的第 " + (index + 1) + " 个流程建议缺少名称");
            }
            BusinessProcessDTO dto = new BusinessProcessDTO();
            dto.setApplicationId(String.valueOf(applicationId));
            dto.setSubjectObjectId(String.valueOf(subjectObjectId));
            dto.setProcessCode(StringUtils.trimToNull(suggestion.getProcessCode()));
            dto.setProcessName(processName);
            dto.setProcessDescription(StringUtils.trimToNull(suggestion.getProcessDescription()));
            dto.setStatus(EnableStatus.ENABLED.getCode());
            processes.add(businessProcessService.create(dto));
        }
        return List.copyOf(processes);
    }

    private List<LowcodeDataModelDTO> resolveModels(LowcodeAiAppGenerateResult plan) {
        if (plan == null) {
            throw new BusinessException("AI 应用方案不能为空");
        }
        if (plan.getModels() != null && !plan.getModels().isEmpty()) {
            return plan.getModels().stream().filter(item -> item != null).toList();
        }
        return plan.getModelDraft() == null ? List.of() : List.of(plan.getModelDraft());
    }

    private List<LowcodeAppDraftDTO> resolveApps(LowcodeAiAppGenerateResult plan) {
        if (plan.getApps() != null && !plan.getApps().isEmpty()) {
            return plan.getApps().stream().filter(item -> item != null).toList();
        }
        return plan.getAppDraft() == null ? List.of() : List.of(plan.getAppDraft());
    }

    private void validateModel(LowcodeDataModelDTO model, int index) {
        if (model == null || model.getModelSchema() == null
                || model.getModelSchema().getFields() == null
                || model.getModelSchema().getFields().stream().noneMatch(field -> field != null)) {
            throw new BusinessException("AI 方案中的第 " + (index + 1) + " 个数据模型缺少字段");
        }
    }

    private LowcodePageSchema resolvePageSchema(
            LowcodeDataModelDTO model,
            List<LowcodeAppDraftDTO> apps,
            LowcodeAiAppGenerateResult plan) {
        String modelCode = StringUtils.trimToNull(model.getModelCode());
        LowcodeAppDraftDTO matched = apps.stream()
                .filter(app -> StringUtils.equals(modelCode, StringUtils.trimToNull(app.getObjectCode())))
                .findFirst()
                .orElse(apps.isEmpty() ? null : apps.get(0));
        LowcodePageSchema pageSchema = matched == null ? null : matched.getPageSchema();
        if (pageSchema == null && modelsCount(plan) == 1) {
            pageSchema = plan.getPageSchema();
        }
        return pageSchema == null ? null : copy(pageSchema, LowcodePageSchema.class);
    }

    private int modelsCount(LowcodeAiAppGenerateResult plan) {
        return plan.getModels() != null && !plan.getModels().isEmpty()
                ? plan.getModels().size() : plan.getModelDraft() == null ? 0 : 1;
    }

    private String resolveDisplayField(LowcodeModelSchema schema) {
        return schema.getFields().stream()
                .filter(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()))
                .filter(field -> StringUtils.isNotBlank(field.getField()))
                .filter(field -> isDisplayCandidate(field))
                .map(LowcodeFieldSchema::getField)
                .findFirst()
                .orElseGet(() -> schema.getFields().stream()
                        .filter(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()))
                        .map(LowcodeFieldSchema::getField)
                        .filter(StringUtils::isNotBlank)
                        .findFirst().orElse(null));
    }

    private boolean isDisplayCandidate(LowcodeFieldSchema field) {
        String name = StringUtils.defaultString(field.getField()).toLowerCase(Locale.ROOT);
        String label = StringUtils.defaultString(field.getLabel());
        return name.contains("name") || name.contains("title") || name.contains("code")
                || label.contains("名称") || label.contains("标题") || label.contains("编码");
    }

    private <T> T copy(Object value, Class<T> targetType) {
        return objectMapper.convertValue(value, targetType);
    }
}
