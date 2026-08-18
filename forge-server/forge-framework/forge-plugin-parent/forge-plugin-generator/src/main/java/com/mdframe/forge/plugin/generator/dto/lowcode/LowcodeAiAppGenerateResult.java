package com.mdframe.forge.plugin.generator.dto.lowcode;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 低代码 AI 应用生成结果。该结果只作为草稿建议，必须由用户确认后保存。
 */
@Data
public class LowcodeAiAppGenerateResult {

    private String domainSuggestion;

    private String requirementSummary;

    private List<LowcodeAiAgentStepDTO> steps = new ArrayList<>();

    private List<LowcodeAiDecisionDTO> decisions = new ArrayList<>();

    private List<LowcodeAiDomainDraftDTO> domains = new ArrayList<>();

    private LowcodeDataModelDTO modelDraft;

    private List<LowcodeDataModelDTO> models = new ArrayList<>();

    private LowcodeAppDraftDTO appDraft;

    private List<LowcodeAppDraftDTO> apps = new ArrayList<>();

    private List<LowcodeProcessSuggestionDTO> processSuggestions = new ArrayList<>();

    private LowcodeModelSchema modelSchema;

    private LowcodePageSchema pageSchema;

    private List<String> ddlPreview = new ArrayList<>();

    private List<String> generationNotes = new ArrayList<>();

    private Boolean fallback = false;
}
