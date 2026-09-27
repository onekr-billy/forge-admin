package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAgentStepDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateRequest;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiDecisionDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiDomainDraftDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAppDraftDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDataModelDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDomainRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeObjectSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeProcessSuggestionDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import com.mdframe.forge.plugin.generator.service.AiClientAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 低代码 AI 应用生成编排。先尝试复用既有 AI 调用链，失败时回落到 Agent 形态规则规划。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowcodeAiGenerateService {

    private static final String AGENT_CODE = "lowcode_system_generator";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String SIMPLE_LAYOUT = "simple-crud";
    private static final String TREE_LAYOUT = "tree-crud";

    private final ObjectMapper objectMapper;
    private final LowcodeDomainService domainService;
    private final LowcodeRuntimeConfigBuilder runtimeConfigBuilder;
    private final AiClientAdapter aiClientAdapter;
    private final LowcodeModelSchemaNormalizer schemaNormalizer;
    private final LowcodePolicyService policyService;

    public LowcodeAiAppGenerateResult generateAppDraft(LowcodeAiAppGenerateRequest request) {
        validateRequest(request);
        LowcodeAiAppGenerateResult aiResult = tryGenerateWithAi(request);
        if (aiResult != null) {
            return aiResult;
        }
        return buildRuleAgentResult(request);
    }

    public Flux<ServerSentEvent<String>> streamGenerateApp(LowcodeAiAppGenerateRequest request) {
        validateRequest(request);
        Map<String, String> contextVars = buildContextVars(request);
        String message = buildAgentMessage(request);
        StringBuilder aiContent = new StringBuilder();
        AtomicReference<String> stageRef = new AtomicReference<>("analyzing");

        Flux<ServerSentEvent<String>> aiStream = aiClientAdapter.stream(
                        request.getDescription(),
                        request.getSessionId(),
                        AGENT_CODE,
                        message,
                        contextVars,
                        request.getProviderId(),
                        request.getModelId(),
                        request.getTemperature() == null ? null : request.getTemperature().doubleValue(),
                        request.getMaxTokens())
                .concatMap(chunk -> {
                    if (StringUtils.isBlank(chunk)) {
                        return Flux.empty();
                    }
                    aiContent.append(chunk);
                    List<ServerSentEvent<String>> events = new ArrayList<>();
                    Map<String, Object> stageProgress = detectStreamStage(aiContent.toString(), stageRef);
                    if (stageProgress != null) {
                        events.add(event("progress", stageProgress));
                    }
                    events.add(event("chunk", Map.of("content", chunk)));
                    return Flux.fromIterable(events);
                })
                .onErrorResume(e -> {
                    log.warn("[LowcodeAiGenerateService] AI stream failed, fallback to rule agent: {}", e.getMessage());
                    return Flux.empty();
                });

        return Flux.concat(
                Flux.just(event("progress", progress("analyzing", "理解业务需求", "正在提取业务目标、对象、字段和约束", "running", null))),
                aiStream,
                Flux.defer(() -> finishStreamGenerate(request, aiContent.toString()))
        );
    }

    public LowcodeAiAppGenerateResult refineApp(Long appId, LowcodeAiAppGenerateRequest request) {
        LowcodeAiAppGenerateResult result = generateAppDraft(request);
        List<String> notes = new ArrayList<>(safeList(result.getGenerationNotes()));
        notes.add("基于应用 " + appId + " 生成了优化建议草稿，尚未保存。");
        result.setGenerationNotes(notes);
        return result;
    }

    private LowcodeAiAppGenerateResult tryGenerateWithAi(LowcodeAiAppGenerateRequest request) {
        try {
            AiClientAdapter.AiClientResult aiResult = aiClientAdapter.call(
                    AGENT_CODE,
                    buildAgentMessage(request),
                    buildContextVars(request),
                    90);
            if (aiResult == null || aiResult.isFallback() || StringUtils.isBlank(aiResult.getContent())) {
                log.info("[LowcodeAiGenerateService] AI unavailable, fallback={}",
                        aiResult == null ? null : aiResult.getFallbackReason());
                return null;
            }
            LowcodeAiAppGenerateResult normalized = parseAiGenerateResult(aiResult.getContent(), request);
            if (normalized == null) {
                return null;
            }
            normalized.setFallback(false);
            return normalized;
        } catch (Exception e) {
            log.info("[LowcodeAiGenerateService] AI result ignored and fallback to rule agent: {}", e.getMessage());
            return null;
        }
    }

    private LowcodeAiAppGenerateResult normalizeAiResult(LowcodeAiAppGenerateResult result,
                                                         LowcodeAiAppGenerateRequest request) {
        if (result == null) {
            return null;
        }
        normalizeResultCollections(result);
        if (result.getModels().isEmpty() && result.getModelDraft() != null) {
            result.getModels().add(result.getModelDraft());
        }
        if (result.getApps().isEmpty() && result.getAppDraft() != null) {
            result.getApps().add(result.getAppDraft());
        }
        if (result.getModels().isEmpty() || result.getApps().isEmpty()) {
            return null;
        }
        AiLowcodeDomain selectedDomain = request.getDomainId() == null ? null : domainService.requireEnabledDomain(request.getDomainId());
        if (result.getDomains().isEmpty()) {
            LowcodeAiDomainDraftDTO domainDraft = selectedDomain != null
                    ? domainPlanner().domainDraftFromEntity(selectedDomain)
                    : domainPlanner().defaultDomainDraft(request.getDescription());
            result.getDomains().add(domainDraft);
        }
        if (selectedDomain != null) {
            LowcodeAiDomainDraftDTO domainDraft = domainPlanner().domainDraftFromEntity(selectedDomain);
            domainDraft.setObjectCodes(resolveGeneratedObjectCodes(result));
            result.getDomains().clear();
            result.getDomains().add(domainDraft);
        }
        domainPlanner().applySingleDomainPreference(result, selectedDomain, request);
        domainPlanner().applyRequestedTablePrefix(result, request.getDescription());
        for (LowcodeAiDomainDraftDTO domainDraft : result.getDomains()) {
            domainPlanner().normalizeDomainDraft(domainDraft, request.getDescription());
        }
        LowcodeAiDomainDraftDTO primaryDomain = result.getDomains().get(0);
        for (LowcodeDataModelDTO model : result.getModels()) {
            normalizeModelDraft(model, primaryDomain, request.getDescription());
        }
        for (LowcodeAppDraftDTO app : result.getApps()) {
            LowcodeDataModelDTO model = resolveModelForApp(result.getModels(), app);
            LowcodeModelSchema modelSchema = model.getModelSchema();
            LowcodePageSchema pageSchema = app.getPageSchema() == null
                    ? buildPageSchema(modelSchema, SIMPLE_LAYOUT, false)
                    : app.getPageSchema();
            String layoutType = chooseSupportedLayout(pageSchema.getLayoutType(), modelSchema);
            pageSchema.setLayoutType(layoutType);
            fillAppDraft(app, primaryDomain, model, modelSchema, pageSchema);
        }
        normalizeProcessSuggestions(result, request.getDescription());
        result.setModelDraft(result.getModels().get(0));
        result.setAppDraft(result.getApps().get(0));
        result.setModelSchema(result.getModelDraft().getModelSchema());
        result.setPageSchema(result.getAppDraft().getPageSchema());
        if (StringUtils.isBlank(result.getRequirementSummary())) {
            result.setRequirementSummary(summarizeRequirement(request.getDescription()));
        }
        if (result.getSteps().isEmpty()) {
            result.setSteps(completedSteps("AI 已返回低代码协议，系统已完成兼容性校验"));
        }
        if (result.getGenerationNotes().isEmpty()) {
            result.setGenerationNotes(List.of("已根据需求生成业务领域、数据模型和应用草稿", "AI 结果不会自动保存，请确认后保存"));
        }
        validateRuntime(result);
        return result;
    }

    private LowcodeAiAppGenerateResult buildRuleAgentResult(LowcodeAiAppGenerateRequest request) {
        AiLowcodeDomain selectedDomain = request.getDomainId() == null ? null : domainService.requireEnabledDomain(request.getDomainId());
        List<LowcodeAiObjectPlan> objectPlans = domainPlanner().inferObjectPlans(
                request.getDescription(), selectedDomain);
        List<LowcodeAiDomainDraftDTO> domainDrafts = domainPlanner().buildDomainDrafts(
                objectPlans, selectedDomain, request.getDescription());
        domainPlanner().applyRequestedTablePrefixToDomains(domainDrafts, request.getDescription());
        Map<String, LowcodeAiDomainDraftDTO> domainMap = new LinkedHashMap<>();
        for (LowcodeAiDomainDraftDTO domainDraft : domainDrafts) {
            domainMap.put(domainDraft.getDomainCode(), domainDraft);
        }

        List<LowcodeDataModelDTO> modelDrafts = new ArrayList<>();
        List<LowcodeAppDraftDTO> appDrafts = new ArrayList<>();
        List<LowcodeAiDecisionDTO> decisions = new ArrayList<>();
        int sort = 0;
        for (LowcodeAiObjectPlan objectPlan : objectPlans) {
            LowcodeAiDomainDraftDTO domainDraft = domainMap.get(objectPlan.domainCode());
            if (domainDraft == null) {
                domainDraft = domainDrafts.get(0);
            }
            String layoutType = chooseLayoutForObject(objectPlan, request.getDescription());
            LowcodeModelSchema modelSchema = buildModelSchema(domainDraft, objectPlan, request.getDescription(), TREE_LAYOUT.equals(layoutType));
            LowcodePageSchema pageSchema = buildPageSchema(modelSchema, layoutType, TREE_LAYOUT.equals(layoutType));
            LowcodeRuntimeConfig runtimeConfig = runtimeConfigBuilder.buildRuntimeConfig(
                    normalizeConfigKey(StringUtils.defaultIfBlank(domainDraft.getConfigKeyPrefix(), domainDraft.getDomainCode() + "_") + objectPlan.code()),
                    modelSchema,
                    pageSchema);

            LowcodeDataModelDTO modelDraft = new LowcodeDataModelDTO();
            modelDraft.setDomainId(domainDraft.getExistingDomainId());
            modelDraft.setModelCode(objectPlan.code());
            modelDraft.setModelName(objectPlan.name());
            modelDraft.setModelDesc(request.getDescription());
            modelDraft.setStatus(STATUS_ENABLED);
            modelDraft.setTenantEnabled(true);
            modelDraft.setMasterData(false);
            modelDraft.setModelSchema(modelSchema);
            modelDraft.setSyncDdl(false);
            modelDraft.setConfirmSyncDdl(false);
            modelDrafts.add(modelDraft);

            LowcodeAppDraftDTO appDraft = new LowcodeAppDraftDTO();
            appDraft.setDomainId(domainDraft.getExistingDomainId());
            appDraft.setDomainCode(domainDraft.getDomainCode());
            appDraft.setDomainName(domainDraft.getDomainName());
            appDraft.setObjectCode(objectPlan.code());
            appDraft.setObjectName(objectPlan.name());
            appDraft.setConfigKey(runtimeConfig.getConfigKey());
            appDraft.setAppName(objectPlan.name() + "管理");
            appDraft.setMenuName(appDraft.getAppName());
            appDraft.setMenuParentId(domainDraft.getMenuParentId());
            appDraft.setMenuSort(sort++);
            appDraft.setModelSchema(modelSchema);
            appDraft.setPageSchema(pageSchema);
            appDrafts.add(appDraft);

            decisions.add(decision(
                    "template",
                    "页面模板选择",
                    objectPlan.name(),
                    layoutLabel(layoutType),
                    buildTemplateReason(objectPlan, layoutType, request.getDescription()),
                    Map.of("layoutType", layoutType, "objectCode", objectPlan.code())));
        }

        LowcodeAiAppGenerateResult result = new LowcodeAiAppGenerateResult();
        result.setRequirementSummary(summarizeRequirement(request.getDescription()));
        result.setDomainSuggestion(domainDrafts.stream().map(LowcodeAiDomainDraftDTO::getDomainName).findFirst().orElse("业务领域"));
        result.setDomains(domainDrafts);
        result.setModels(modelDrafts);
        result.setModelDraft(modelDrafts.get(0));
        result.setApps(appDrafts);
        result.setAppDraft(appDrafts.get(0));
        result.setProcessSuggestions(inferProcessSuggestions(modelDrafts, request.getDescription()));
        result.setModelSchema(modelDrafts.get(0).getModelSchema());
        result.setPageSchema(appDrafts.get(0).getPageSchema());
        result.setSteps(completedSteps("已通过规则 Agent 完成端到端草稿生成"));
        result.setDecisions(buildDecisions(domainDrafts, objectPlans, decisions));
        result.setFallback(true);
        result.setGenerationNotes(List.of(
                "已自动划分业务领域并生成模型与应用草稿，确认前不会保存任何数据",
                "页面模板由系统根据需求关键词和模型结构自动选择，后续可在应用设计器继续调整",
                "涉及审批或流转的需求会生成可继续设计的业务流程草稿，不会自动部署 Flowable",
                "当前未执行 DDL，不会自动发布应用"
        ));
        return result;
    }

    private void validateRequest(LowcodeAiAppGenerateRequest request) {
        if (request == null || StringUtils.isBlank(request.getDescription())) {
            throw new BusinessException("需求描述不能为空");
        }
    }

    private String buildAgentMessage(LowcodeAiAppGenerateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下业务需求生成低代码业务系统草稿，只返回符合 Agent 上下文规范的 JSON。\n\n");
        sb.append("## 业务需求\n").append(request.getDescription()).append("\n\n");
        if (request.getDomainId() != null) {
            AiLowcodeDomain domain = domainService.requireEnabledDomain(request.getDomainId());
            sb.append("## 用户指定的目标业务领域\n");
            sb.append("请优先在该已有业务领域内生成模型和应用，domains[0].existingDomainId 必须使用该领域 ID；除非需求明确跨领域，否则不要新建领域。\n");
            sb.append("- domainId: ").append(domain.getId()).append("\n");
            sb.append("- domainCode: ").append(domain.getDomainCode()).append("\n");
            sb.append("- domainName: ").append(domain.getDomainName()).append("\n\n");
        }
        if (!safeList(request.getExistingModels()).isEmpty()) {
            sb.append("## 当前领域已有数据模型\n");
            sb.append("请优先复用这些模型生成页面；只有需求明确缺少对象时才补充新模型。\n");
            sb.append(summarizeExistingModels(request.getExistingModels())).append("\n\n");
        }
        if (StringUtils.isNotBlank(request.getDraftContext())) {
            sb.append("## 当前已生成草稿\n");
            sb.append("用户正在基于这份草稿追加要求，请在保留合理结构的基础上调整，不要忽略已有草稿。\n");
            sb.append(trimForPrompt(request.getDraftContext(), 6000)).append("\n\n");
        }
        sb.append("## 运行时变量\n");
        sb.append("- allowAutoSave: false\n");
        sb.append("- supportedLayouts: simple-crud, tree-crud, master-detail-crud\n");
        sb.append("- firstPhaseCodegen: single-main-model\n");
        sb.append("- processSuggestions: 仅在需求包含审批或业务流转时返回，引用 models[].modelCode；只生成设计建议，不部署流程\n");
        return sb.toString();
    }

    private String summarizeExistingModels(List<LowcodeDataModelDTO> models) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (LowcodeDataModelDTO model : safeList(models)) {
            LowcodeModelSchema schema = model.getModelSchema();
            sb.append(index++).append(". ")
                    .append(StringUtils.defaultIfBlank(model.getModelName(), "-"))
                    .append(" / ")
                    .append(StringUtils.defaultIfBlank(model.getModelCode(), "-"));
            if (schema != null) {
                sb.append(" / table=").append(StringUtils.defaultIfBlank(schema.getTableName(), "-"));
                List<LowcodeFieldSchema> fields = safeList(schema.getFields());
                if (!fields.isEmpty()) {
                    sb.append(" / fields=");
                    sb.append(fields.stream()
                            .limit(12)
                            .map(field -> StringUtils.defaultIfBlank(field.getField(), "-") + ":" + StringUtils.defaultIfBlank(field.getLabel(), "-"))
                            .toList());
                }
            }
            sb.append("\n");
        }
        return trimForPrompt(sb.toString(), 5000);
    }

    private String trimForPrompt(String text, int maxLength) {
        String value = StringUtils.defaultString(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...已截断";
    }

    private Map<String, String> buildContextVars(LowcodeAiAppGenerateRequest request) {
        Map<String, String> contextVars = new HashMap<>();
        contextVars.put("description", request.getDescription());
        if (request.getDomainId() != null) {
            AiLowcodeDomain domain = domainService.requireEnabledDomain(request.getDomainId());
            contextVars.put("domainId", String.valueOf(domain.getId()));
            contextVars.put("domainCode", domain.getDomainCode());
            contextVars.put("domainName", domain.getDomainName());
        }
        return contextVars;
    }

    private LowcodeAiAppGenerateResult parseAiGenerateResult(String content, LowcodeAiAppGenerateRequest request) {
        try {
            String json = extractJson(extractAnswerContent(content));
            if (StringUtils.isBlank(json)) {
                return null;
            }
            LowcodeAiAppGenerateResult result = objectMapper.readValue(json, LowcodeAiAppGenerateResult.class);
            return normalizeAiResult(result, request);
        } catch (Exception e) {
            log.info("[LowcodeAiGenerateService] AI stream result parse failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractAnswerContent(String content) {
        String text = StringUtils.defaultString(content);
        String answerDelimiter = "==================== 完整回复 ====================";
        int answerIndex = text.indexOf(answerDelimiter);
        if (answerIndex >= 0) {
            return text.substring(answerIndex + answerDelimiter.length());
        }
        return text;
    }

    private Flux<ServerSentEvent<String>> finishStreamGenerate(LowcodeAiAppGenerateRequest request, String content) {
        try {
            LowcodeAiAppGenerateResult result = parseAiGenerateResult(content, request);
            if (result == null) {
                result = buildRuleAgentResult(request);
            } else {
                result.setFallback(false);
            }
            return Flux.just(
                    event("progress", progress("validating", "校验低代码协议", "正在校验模型字段、页面分区和运行时协议", "running", null)),
                    event("progress", progress("validating", "校验低代码协议", "已完成模型和页面协议校验，等待用户确认保存", "completed", summarizeApps(result))),
                    event("result", result),
                    event("complete", Map.of("message", "生成完成，请确认后保存模型和应用草稿"))
            );
        } catch (Exception e) {
            log.error("[LowcodeAiGenerateService] stream generate failed", e);
            return Flux.just(event("error", Map.of("message", StringUtils.defaultIfBlank(e.getMessage(), "生成失败"))));
        }
    }

    private Map<String, Object> detectStreamStage(String content, AtomicReference<String> stageRef) {
        String nextStage = resolveStreamStage(content);
        String current = stageRef.get();
        if (StringUtils.isBlank(nextStage) || nextStage.equals(current)) {
            return null;
        }
        stageRef.set(nextStage);
        return switch (nextStage) {
            case "domain-planning" -> progress("domain-planning", "划分业务领域", "模型正在输出领域归属和领域复用策略", "running", null);
            case "model-generating" -> progress("model-generating", "生成数据模型", "模型正在输出业务对象、字段、字典和安全策略", "running", null);
            case "page-generating" -> progress("page-generating", "生成应用页面", "模型正在输出页面模板、分区和应用草稿", "running", null);
            default -> null;
        };
    }

    private String resolveStreamStage(String content) {
        String text = StringUtils.defaultString(content);
        if (text.contains("\"apps\"") || text.contains("\"appDraft\"") || text.contains("\"pageSchema\"")) {
            return "page-generating";
        }
        if (text.contains("\"models\"") || text.contains("\"modelDraft\"") || text.contains("\"modelSchema\"")) {
            return "model-generating";
        }
        if (text.contains("\"domains\"") || text.contains("\"domainSuggestion\"")) {
            return "domain-planning";
        }
        return "analyzing";
    }

    private List<String> resolveGeneratedObjectCodes(LowcodeAiAppGenerateResult result) {
        Set<String> codes = new LinkedHashSet<>();
        for (LowcodeDataModelDTO model : safeList(result.getModels())) {
            if (StringUtils.isNotBlank(model.getModelCode())) {
                codes.add(model.getModelCode());
            }
        }
        for (LowcodeAppDraftDTO app : safeList(result.getApps())) {
            if (StringUtils.isNotBlank(app.getObjectCode())) {
                codes.add(app.getObjectCode());
            }
        }
        return new ArrayList<>(codes);
    }

    private void normalizeResultCollections(LowcodeAiAppGenerateResult result) {
        if (result.getSteps() == null) {
            result.setSteps(new ArrayList<>());
        }
        if (result.getDecisions() == null) {
            result.setDecisions(new ArrayList<>());
        }
        if (result.getDomains() == null) {
            result.setDomains(new ArrayList<>());
        }
        if (result.getModels() == null) {
            result.setModels(new ArrayList<>());
        }
        if (result.getApps() == null) {
            result.setApps(new ArrayList<>());
        }
        if (result.getProcessSuggestions() == null) {
            result.setProcessSuggestions(new ArrayList<>());
        }
        if (result.getGenerationNotes() == null) {
            result.setGenerationNotes(new ArrayList<>());
        }
        if (result.getDdlPreview() == null) {
            result.setDdlPreview(new ArrayList<>());
        }
    }

    private void normalizeProcessSuggestions(LowcodeAiAppGenerateResult result, String description) {
        if (result.getProcessSuggestions().isEmpty()) {
            result.setProcessSuggestions(inferProcessSuggestions(result.getModels(), description));
            return;
        }
        List<String> modelCodes = result.getModels().stream()
                .map(LowcodeDataModelDTO::getModelCode)
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .toList();
        String defaultSubject = modelCodes.isEmpty() ? null : modelCodes.get(0);
        List<LowcodeProcessSuggestionDTO> normalized = new ArrayList<>();
        for (LowcodeProcessSuggestionDTO source : result.getProcessSuggestions()) {
            if (source == null || normalized.size() >= 3) {
                continue;
            }
            String subjectCode = StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(source.getSubjectObjectCode()), defaultSubject);
            if (subjectCode == null || !modelCodes.contains(subjectCode)) {
                continue;
            }
            LowcodeProcessSuggestionDTO suggestion = new LowcodeProcessSuggestionDTO();
            suggestion.setSubjectObjectCode(subjectCode);
            suggestion.setProcessName(StringUtils.left(StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(source.getProcessName()), "业务审批流程"), 128));
            suggestion.setProcessCode(normalizeCode(StringUtils.defaultIfBlank(
                    source.getProcessCode(), subjectCode + "_approval"), "business_approval", 128));
            suggestion.setProcessDescription(StringUtils.left(StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(source.getProcessDescription()),
                    "由 AI 应用方案生成的业务流程草稿，节点和表单需在流程设计器中确认。"), 500));
            normalized.add(suggestion);
        }
        result.setProcessSuggestions(normalized);
    }

    private List<LowcodeProcessSuggestionDTO> inferProcessSuggestions(
            List<LowcodeDataModelDTO> models, String description) {
        if (!containsAny(StringUtils.defaultString(description),
                Set.of("审批", "审核", "申请", "流转", "流程", "会签", "驳回"))) {
            return new ArrayList<>();
        }
        LowcodeDataModelDTO subject = safeList(models).stream()
                .filter(model -> model != null && StringUtils.isNotBlank(model.getModelCode()))
                .findFirst().orElse(null);
        if (subject == null) {
            return new ArrayList<>();
        }
        String subjectName = StringUtils.defaultIfBlank(subject.getModelName(), "业务单据");
        LowcodeProcessSuggestionDTO suggestion = new LowcodeProcessSuggestionDTO();
        suggestion.setSubjectObjectCode(subject.getModelCode());
        suggestion.setProcessCode(normalizeCode(
                subject.getModelCode() + "_approval", "business_approval", 128));
        suggestion.setProcessName(StringUtils.left(subjectName + "审批流程", 128));
        suggestion.setProcessDescription(StringUtils.left(
                "根据需求生成的" + subjectName + "流程草稿；请在业务流程设计器中补充审批节点、表单权限和发布绑定。", 500));
        return new ArrayList<>(List.of(suggestion));
    }

    private void normalizeModelDraft(LowcodeDataModelDTO model, LowcodeAiDomainDraftDTO domainDraft, String description) {
        if (model.getModelSchema() == null) {
            model.setModelSchema(new LowcodeModelSchema());
        }
        String modelCode = normalizeCode(StringUtils.defaultIfBlank(model.getModelCode(),
                model.getModelSchema().getObject() == null ? null : model.getModelSchema().getObject().getCode()), "ai_model", 48);
        String modelName = StringUtils.defaultIfBlank(model.getModelName(),
                StringUtils.defaultIfBlank(model.getModelSchema().getBusinessName(), "业务对象"));
        model.setDomainId(domainDraft.getExistingDomainId());
        model.setModelCode(modelCode);
        model.setModelName(modelName);
        model.setModelDesc(StringUtils.defaultIfBlank(model.getModelDesc(), description));
        model.setStatus(StringUtils.defaultIfBlank(model.getStatus(), STATUS_ENABLED));
        model.setTenantEnabled(model.getTenantEnabled() == null || model.getTenantEnabled());
        model.setMasterData(Boolean.TRUE.equals(model.getMasterData()));
        model.setSyncDdl(false);
        model.setConfirmSyncDdl(false);

        LowcodeModelSchema schema = model.getModelSchema();
        schema.setSchemaVersion(2);
        schema.setAppType(StringUtils.defaultIfBlank(schema.getAppType(), "SINGLE"));
        schema.setTableMode(StringUtils.defaultIfBlank(schema.getTableMode(), "CREATE"));
        String requestedTablePrefix = parseRequestedTablePrefix(description);
        String tableName = normalizeTableName(StringUtils.defaultIfBlank(schema.getTableName(), domainDraft.getTablePrefix() + modelCode));
        if (StringUtils.isNotBlank(requestedTablePrefix) && !tableName.startsWith(requestedTablePrefix)) {
            tableName = normalizeTableName(requestedTablePrefix + modelCode);
        }
        schema.setTableName(tableName);
        schema.setBusinessName(modelName);
        LowcodeDomainRef domainRef = schema.getDomain() == null ? new LowcodeDomainRef() : schema.getDomain();
        domainRef.setId(domainDraft.getExistingDomainId());
        domainRef.setCode(domainDraft.getDomainCode());
        domainRef.setName(domainDraft.getDomainName());
        schema.setDomain(domainRef);
        LowcodeObjectSchema object = schema.getObject() == null ? new LowcodeObjectSchema() : schema.getObject();
        object.setCode(modelCode);
        object.setName(modelName);
        object.setDescription(StringUtils.defaultIfBlank(object.getDescription(), description));
        schema.setObject(object);
        if (schema.getFields() == null || schema.getFields().isEmpty()) {
            schema.setFields(fieldTemplateCatalog().defaultFields(modelName));
        }
        schemaNormalizer.normalizeModelFields(schema, true);
        policyService.normalizeModelSchema(schema);
    }

    private LowcodeDataModelDTO resolveModelForApp(List<LowcodeDataModelDTO> models, LowcodeAppDraftDTO app) {
        if (app != null && StringUtils.isNotBlank(app.getObjectCode())) {
            return models.stream()
                    .filter(model -> app.getObjectCode().equals(model.getModelCode()))
                    .findFirst()
                    .orElse(models.get(0));
        }
        return models.get(0);
    }

    private void fillAppDraft(LowcodeAppDraftDTO app, LowcodeAiDomainDraftDTO domainDraft, LowcodeDataModelDTO model,
                              LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        LowcodeRuntimeConfig runtimeConfig = runtimeConfigBuilder.buildRuntimeConfig(
                normalizeConfigKey(StringUtils.defaultIfBlank(app.getConfigKey(), domainDraft.getConfigKeyPrefix() + model.getModelCode())),
                modelSchema,
                pageSchema);
        app.setDomainId(domainDraft.getExistingDomainId());
        app.setDomainCode(domainDraft.getDomainCode());
        app.setDomainName(domainDraft.getDomainName());
        app.setObjectCode(model.getModelCode());
        app.setObjectName(model.getModelName());
        app.setConfigKey(runtimeConfig.getConfigKey());
        app.setAppName(StringUtils.defaultIfBlank(app.getAppName(), model.getModelName() + "管理"));
        app.setMenuName(StringUtils.defaultIfBlank(app.getMenuName(), app.getAppName()));
        app.setMenuParentId(app.getMenuParentId() == null ? domainDraft.getMenuParentId() : app.getMenuParentId());
        app.setMenuSort(app.getMenuSort() == null ? 0 : app.getMenuSort());
        app.setModelSchema(modelSchema);
        app.setPageSchema(pageSchema);
    }

    private void validateRuntime(LowcodeAiAppGenerateResult result) {
        for (LowcodeAppDraftDTO app : result.getApps()) {
            runtimeConfigBuilder.buildRuntimeConfig(app.getConfigKey(), app.getModelSchema(), app.getPageSchema());
        }
    }

    private LowcodeAiDomainPlanningStrategy domainPlanner() {
        return new LowcodeAiDomainPlanningStrategy(domainService);
    }

    private LowcodeModelSchema buildModelSchema(LowcodeAiDomainDraftDTO domainDraft, LowcodeAiObjectPlan objectPlan,
                                                String description, boolean treeLayout) {
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setSchemaVersion(2);
        schema.setAppType(treeLayout ? "TREE" : "SINGLE");
        schema.setTableMode("CREATE");
        schema.setTableName(normalizeTableName(StringUtils.defaultIfBlank(domainDraft.getTablePrefix(), "biz_") + objectPlan.code()));
        schema.setBusinessName(objectPlan.name());

        LowcodeDomainRef domainRef = new LowcodeDomainRef();
        domainRef.setId(domainDraft.getExistingDomainId());
        domainRef.setCode(domainDraft.getDomainCode());
        domainRef.setName(domainDraft.getDomainName());
        schema.setDomain(domainRef);

        LowcodeObjectSchema object = new LowcodeObjectSchema();
        object.setCode(objectPlan.code());
        object.setName(objectPlan.name());
        object.setDescription(description);
        schema.setObject(object);

        List<LowcodeFieldSchema> fields = new ArrayList<>(fieldTemplateCatalog().fieldsForObject(objectPlan));
        if (treeLayout) {
            fields.add(0, fieldTemplateCatalog().field(
                    "parentId", "parent_id", "上级节点", "bigint", null,
                    false, false, false, true, "treeSelect"));
            LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
            treeConfig.setEnabled(true);
            treeConfig.setKeyField("id");
            treeConfig.setParentField("parentId");
            treeConfig.setLabelField(resolveLabelField(fields));
            treeConfig.setChildrenField("children");
            treeConfig.setTreeTitle(objectPlan.name() + "树");
            treeConfig.setLoadMode("lazy");
            schema.setTreeConfig(treeConfig);
        }
        schema.setFields(fields);
        schemaNormalizer.normalizeModelFields(schema, true);
        policyService.normalizeModelSchema(schema);
        return schema;
    }

    private LowcodePageSchema buildPageSchema(LowcodeModelSchema modelSchema, String layoutType, boolean treeLayout) {
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setLayoutType(StringUtils.defaultIfBlank(layoutType, SIMPLE_LAYOUT));
        pageSchema.setPrimaryModelCode(modelSchema.getObject() == null ? null : modelSchema.getObject().getCode());
        pageSchema.getZones().add(fieldTemplateCatalog().zone("search", "search-form", modelSchema.getFields().stream()
                .filter(field -> Boolean.TRUE.equals(field.getSearchable()))
                .map(LowcodeFieldSchema::getField)
                .limit(4)
                .toList()));
        LowcodePageZone tableZone = fieldTemplateCatalog().zone("table", "data-table", modelSchema.getFields().stream()
                .filter(field -> Boolean.TRUE.equals(field.getListVisible()))
                .map(LowcodeFieldSchema::getField)
                .limit(7)
                .toList());
        if (treeLayout) {
            Map<String, Object> treeConfig = new LinkedHashMap<>();
            treeConfig.put("keyField", "id");
            treeConfig.put("parentField", "parentId");
            treeConfig.put("labelField", resolveLabelField(modelSchema.getFields()));
            treeConfig.put("childrenField", "children");
            treeConfig.put("treeTitle", modelSchema.getBusinessName() + "树");
            treeConfig.put("loadMode", "lazy");
            tableZone.getProps().put("treeConfig", treeConfig);
        }
        pageSchema.getZones().add(tableZone);
        pageSchema.getZones().add(fieldTemplateCatalog().zone("edit", "edit-form", modelSchema.getFields().stream()
                .filter(field -> field.getFormVisible() == null || field.getFormVisible())
                .map(LowcodeFieldSchema::getField)
                .toList()));
        pageSchema.getZones().add(fieldTemplateCatalog().zone("detail", "detail-panel", modelSchema.getFields().stream()
                .filter(field -> field.getListVisible() == null || field.getListVisible())
                .map(LowcodeFieldSchema::getField)
                .limit(8)
                .toList()));
        return pageSchema;
    }

    private LowcodeAiFieldTemplateCatalog fieldTemplateCatalog() {
        return new LowcodeAiFieldTemplateCatalog();
    }

    private String chooseLayoutForObject(LowcodeAiObjectPlan objectPlan, String description) {
        if (objectPlan.tree() || shouldUseTree(description, objectPlan.name())) {
            return TREE_LAYOUT;
        }
        return SIMPLE_LAYOUT;
    }

    private String chooseSupportedLayout(String layoutType, LowcodeModelSchema modelSchema) {
        if (TREE_LAYOUT.equals(layoutType)) {
            return TREE_LAYOUT;
        }
        if ("master-detail-crud".equals(layoutType) && modelSchema != null && modelSchema.getChildren() != null
                && !modelSchema.getChildren().isEmpty()) {
            return "master-detail-crud";
        }
        return SIMPLE_LAYOUT;
    }

    private boolean shouldUseTree(String description, String objectName) {
        return domainPlanner().shouldUseTree(description, objectName);
    }

    private String buildTemplateReason(LowcodeAiObjectPlan objectPlan, String layoutType, String description) {
        if (TREE_LAYOUT.equals(layoutType)) {
            return "需求或对象包含组织、分类、树形、父级等层级关键词，已补充 parentId 字段和树形配置。";
        }
        if (containsAny(description, Set.of("明细", "子表", "子项", "订单行"))) {
            return "识别到明细/子项诉求；首期先生成可运行的单表应用草稿，后续可在应用设计器扩展主子表协议。";
        }
        return "需求以列表、查询、编辑和详情为主，适合标准单表 CRUD 模板。";
    }

    private String layoutLabel(String layoutType) {
        if (TREE_LAYOUT.equals(layoutType)) {
            return "左树右表/树形单表";
        }
        if ("master-detail-crud".equals(layoutType)) {
            return "主子表";
        }
        return "标准单表";
    }

    private List<LowcodeAiDecisionDTO> buildDecisions(List<LowcodeAiDomainDraftDTO> domains,
                                                       List<LowcodeAiObjectPlan> objects,
                                                       List<LowcodeAiDecisionDTO> templateDecisions) {
        List<LowcodeAiDecisionDTO> decisions = new ArrayList<>();
        decisions.add(decision(
                "domain",
                "业务领域划分",
                "业务系统",
                domains.stream().map(LowcodeAiDomainDraftDTO::getDomainName).toList().toString(),
                "根据需求关键词和业务对象边界自动划分领域；已有启用领域会直接复用。",
                Map.of("domainCount", domains.size())));
        decisions.add(decision(
                "model",
                "数据模型规划",
                "业务对象",
                objects.stream().map(LowcodeAiObjectPlan::name).toList().toString(),
                "每个核心业务对象生成一个可独立维护的数据模型，便于后续二次开发和代码下载。",
                Map.of("modelCount", objects.size())));
        decisions.addAll(templateDecisions);
        decisions.add(decision(
                "save",
                "保存策略",
                "模型与应用草稿",
                "用户确认后保存",
                "AI 只生成草稿协议，不自动建表、不自动发布，确认后才写入模型和应用草稿。",
                Map.of("autoSave", false)));
        return decisions;
    }

    private List<LowcodeAiAgentStepDTO> completedSteps(String finalSummary) {
        return List.of(
                step(1, "analyzing", "理解业务需求", "completed", "已提取业务目标、对象、字段和约束", "形成需求摘要"),
                step(2, "domain-planning", "划分业务领域", "completed", "已确定业务领域和对象归属", "领域可复用或新建"),
                step(3, "model-generating", "生成数据模型", "completed", "已生成模型字段、表名和基础策略", "模型确认后保存"),
                step(4, "page-generating", "生成应用页面", "completed", "已自动选择模板并生成页面协议", "应用确认后保存"),
                step(5, "validating", "校验低代码协议", "completed", "已完成运行时协议校验", finalSummary)
        );
    }

    private LowcodeAiAgentStepDTO step(Integer orderNo, String key, String title, String status, String message, String summary) {
        LowcodeAiAgentStepDTO step = new LowcodeAiAgentStepDTO();
        step.setOrderNo(orderNo);
        step.setStepKey(key);
        step.setTitle(title);
        step.setStatus(status);
        step.setMessage(message);
        step.setSummary(summary);
        return step;
    }

    private LowcodeAiDecisionDTO decision(String type, String title, String target, String value, String reason,
                                          Map<String, Object> meta) {
        LowcodeAiDecisionDTO decision = new LowcodeAiDecisionDTO();
        decision.setDecisionType(type);
        decision.setTitle(title);
        decision.setTarget(target);
        decision.setValue(value);
        decision.setReason(reason);
        decision.setMeta(meta == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta));
        return decision;
    }

    private Map<String, Object> progress(String stage, String title, String message, String status, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage);
        data.put("stepKey", stage);
        data.put("title", title);
        data.put("message", message);
        data.put("status", status);
        if (StringUtils.isNotBlank(summary)) {
            data.put("summary", summary);
        }
        return data;
    }

    private String summarizeApps(LowcodeAiAppGenerateResult result) {
        if (result == null || result.getApps() == null || result.getApps().isEmpty()) {
            return null;
        }
        return "已生成 " + result.getApps().size() + " 个应用草稿：" + result.getApps().stream()
                .map(LowcodeAppDraftDTO::getAppName)
                .toList();
    }

    private String summarizeRequirement(String description) {
        String text = StringUtils.normalizeSpace(StringUtils.defaultString(description));
        if (text.length() > 120) {
            return text.substring(0, 120) + "...";
        }
        return text;
    }

    private String resolveLabelField(List<LowcodeFieldSchema> fields) {
        return fields.stream()
                .map(LowcodeFieldSchema::getField)
                .filter(field -> Set.of("name", "title", "label", "customerName", "productName", "departmentName").contains(field))
                .findFirst()
                .orElse(fields.isEmpty() ? "name" : fields.get(0).getField());
    }

    private boolean containsAny(String text, Set<String> keywords) {
        return domainPlanner().containsAny(text, keywords);
    }

    private String normalizeCode(String value, String fallback, int maxLength) {
        return domainPlanner().normalizeCode(value, fallback, maxLength);
    }

    private String parseRequestedTablePrefix(String description) {
        return domainPlanner().parseRequestedTablePrefix(description);
    }

    private String normalizeTableName(String value) {
        return domainPlanner().normalizeTableName(value);
    }

    private String normalizeConfigKey(String value) {
        return domainPlanner().normalizeConfigKey(value);
    }

    private String extractJson(String content) {
        String text = StringUtils.defaultString(content).trim();
        if (text.contains("```json")) {
            text = text.substring(text.indexOf("```json") + 7);
            text = text.substring(0, text.indexOf("```"));
            return text.trim();
        }
        if (text.contains("```")) {
            text = text.substring(text.indexOf("```") + 3);
            text = text.substring(0, text.indexOf("```"));
            return text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private ServerSentEvent<String> event(String event, Object data) {
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(data))
                    .event(event)
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.builder("{\"message\":\"事件序列化失败\"}")
                    .event("error")
                    .build();
        }
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? new ArrayList<>() : source;
    }

}
