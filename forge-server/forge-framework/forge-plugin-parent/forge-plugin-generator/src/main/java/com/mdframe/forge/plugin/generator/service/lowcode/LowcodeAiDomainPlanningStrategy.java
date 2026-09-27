package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateRequest;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiDecisionDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiDomainDraftDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDomainSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 低代码领域与对象规划策略。
 */
@RequiredArgsConstructor
final class LowcodeAiDomainPlanningStrategy {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String SIMPLE_LAYOUT = "simple-crud";
    private static final int MAX_OBJECTS = 6;
    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile(
        "(?:表名|数据表|表).*?(?:以|前缀为|前缀是|前缀)\\s*([A-Za-z][A-Za-z0-9_]*_)");
    private static final Pattern TABLE_PREFIX_FALLBACK_PATTERN = Pattern.compile(
        "([A-Za-z][A-Za-z0-9_]*_)\\s*开头");

    private static final List<DomainCandidate> DOMAIN_CANDIDATES = List.of(
        new DomainCandidate("crm", "客户关系", "客户、合同、商机、回款等客户经营业务", Set.of("客户", "合同", "商机", "线索", "回款", "拜访", "销售")),
        new DomainCandidate("scm", "供应链", "商品、采购、库存、供应商、仓储等供应链业务", Set.of("商品", "采购", "库存", "供应商", "仓库", "入库", "出库")),
        new DomainCandidate("finance", "财务", "发票、账单、费用、付款和预算等财务业务", Set.of("发票", "账单", "付款", "收款", "费用", "预算", "结算")),
        new DomainCandidate("hr", "人力资源", "员工、部门、考勤、请假和薪资等组织人事业务", Set.of("员工", "部门", "岗位", "考勤", "请假", "薪资", "招聘")),
        new DomainCandidate("project", "项目管理", "项目、任务、里程碑、工时和缺陷等项目协作业务", Set.of("项目", "任务", "里程碑", "工时", "缺陷", "迭代")),
        new DomainCandidate("ops", "运营管理", "活动、工单、审批、计划和台账等运营业务", Set.of("活动", "工单", "审批", "计划", "台账", "巡检"))
    );

    private static final List<ObjectCandidate> OBJECT_CANDIDATES = List.of(
        new ObjectCandidate("customer", "客户", "crm", Set.of("客户", "客户档案", "客户管理"), false),
        new ObjectCandidate("contact", "联系人", "crm", Set.of("联系人", "客户联系人"), false),
        new ObjectCandidate("opportunity", "商机", "crm", Set.of("商机", "销售机会"), false),
        new ObjectCandidate("contract", "合同", "crm", Set.of("合同", "协议"), false),
        new ObjectCandidate("payment", "回款", "crm", Set.of("回款", "收款"), false),
        new ObjectCandidate("product", "商品", "scm", Set.of("商品", "产品", "物料"), false),
        new ObjectCandidate("supplier", "供应商", "scm", Set.of("供应商", "供货商"), false),
        new ObjectCandidate("purchase_order", "采购订单", "scm", Set.of("采购订单", "采购单", "采购"), false),
        new ObjectCandidate("sales_order", "销售订单", "scm", Set.of("销售订单", "销售单", "订单"), false),
        new ObjectCandidate("inventory", "库存", "scm", Set.of("库存", "仓储", "入库", "出库"), false),
        new ObjectCandidate("invoice", "发票", "finance", Set.of("发票", "开票"), false),
        new ObjectCandidate("expense", "费用", "finance", Set.of("费用", "报销"), false),
        new ObjectCandidate("employee", "员工", "hr", Set.of("员工", "人员", "职员"), false),
        new ObjectCandidate("department", "部门", "hr", Set.of("部门", "组织架构", "组织树"), true),
        new ObjectCandidate("leave_request", "请假申请", "hr", Set.of("请假", "休假"), false),
        new ObjectCandidate("project", "项目", "project", Set.of("项目"), false),
        new ObjectCandidate("task", "任务", "project", Set.of("任务", "待办"), false),
        new ObjectCandidate("work_order", "工单", "ops", Set.of("工单", "服务单"), false),
        new ObjectCandidate("category", "分类", "ops", Set.of("分类", "类目", "目录", "树形"), true)
    );

    private final LowcodeDomainService domainService;

    List<LowcodeAiObjectPlan> inferObjectPlans(String description, AiLowcodeDomain selectedDomain) {
        String text = StringUtils.defaultString(description);
        Map<String, LowcodeAiObjectPlan> plans = new LinkedHashMap<>();
        for (ObjectCandidate candidate : OBJECT_CANDIDATES) {
            if (containsAny(text, candidate.keywords())) {
                String domainCode = selectedDomain == null
                    ? candidate.domainCode() : selectedDomain.getDomainCode();
                plans.put(candidate.code(), new LowcodeAiObjectPlan(
                    candidate.code(), candidate.name(), domainCode, candidate.tree()));
            }
        }
        if (plans.isEmpty()) {
            DomainCandidate domainCandidate = inferDomainCandidates(description).get(0);
            String objectName = inferObjectName(description);
            String objectCode = normalizeCode(objectName, "ai_model", 48);
            String domainCode = selectedDomain == null
                ? domainCandidate.code() : selectedDomain.getDomainCode();
            plans.put(objectCode, new LowcodeAiObjectPlan(
                objectCode, objectName, domainCode, shouldUseTree(description, objectName)));
        }
        if (selectedDomain == null && isSingleDomainRequested(description)) {
            String primaryDomainCode = plans.values().stream()
                .findFirst()
                .map(LowcodeAiObjectPlan::domainCode)
                .orElseGet(() -> inferDomainCandidates(description).get(0).code());
            Map<String, LowcodeAiObjectPlan> collapsedPlans = new LinkedHashMap<>();
            for (LowcodeAiObjectPlan plan : plans.values()) {
                collapsedPlans.put(plan.code(), new LowcodeAiObjectPlan(
                    plan.code(), plan.name(), primaryDomainCode, plan.tree()));
            }
            plans = collapsedPlans;
        }
        return plans.values().stream().limit(MAX_OBJECTS).toList();
    }

    List<LowcodeAiDomainDraftDTO> buildDomainDrafts(List<LowcodeAiObjectPlan> objectPlans,
                                                     AiLowcodeDomain selectedDomain,
                                                     String description) {
        if (selectedDomain != null) {
            LowcodeAiDomainDraftDTO domainDraft = domainDraftFromEntity(selectedDomain);
            domainDraft.setObjectCodes(objectPlans.stream()
                .map(LowcodeAiObjectPlan::code).distinct().toList());
            return List.of(domainDraft);
        }
        if (isSingleDomainRequested(description)) {
            String primaryDomainCode = objectPlans.stream()
                .findFirst()
                .map(LowcodeAiObjectPlan::domainCode)
                .orElseGet(() -> inferDomainCandidates(description).get(0).code());
            LowcodeAiDomainDraftDTO domainDraft = buildResolvedDomainDraft(
                findDomainCandidate(primaryDomainCode), description);
            domainDraft.setObjectCodes(objectPlans.stream()
                .map(LowcodeAiObjectPlan::code).distinct().toList());
            return List.of(domainDraft);
        }
        Map<String, DomainCandidate> candidates = new LinkedHashMap<>();
        for (DomainCandidate candidate : inferDomainCandidates(description)) {
            candidates.put(candidate.code(), candidate);
        }
        for (LowcodeAiObjectPlan objectPlan : objectPlans) {
            candidates.putIfAbsent(objectPlan.domainCode(), findDomainCandidate(objectPlan.domainCode()));
        }
        List<LowcodeAiDomainDraftDTO> result = new ArrayList<>();
        for (DomainCandidate candidate : candidates.values()) {
            LowcodeAiDomainDraftDTO draft = buildResolvedDomainDraft(candidate, description);
            draft.setObjectCodes(objectPlans.stream()
                .filter(plan -> candidate.code().equals(plan.domainCode()))
                .map(LowcodeAiObjectPlan::code)
                .distinct()
                .toList());
            result.add(draft);
        }
        return result.stream()
            .sorted(Comparator.comparing(LowcodeAiDomainDraftDTO::getDomainCode))
            .toList();
    }

    LowcodeAiDomainDraftDTO domainDraftFromEntity(AiLowcodeDomain domain) {
        LowcodeAiDomainDraftDTO draft = new LowcodeAiDomainDraftDTO();
        draft.setExistingDomainId(domain.getId());
        draft.setDomainCode(domain.getDomainCode());
        draft.setDomainName(domain.getDomainName());
        draft.setDomainDesc(domain.getDomainDesc());
        draft.setIcon(domain.getIcon());
        draft.setSort(domain.getSort());
        draft.setStatus(domain.getStatus());
        draft.setMenuParentId(domain.getMenuParentId());
        draft.setTablePrefix(domain.getTablePrefix());
        draft.setConfigKeyPrefix(domain.getConfigKeyPrefix());
        draft.setDefaultAppType(domain.getDefaultAppType());
        draft.setDefaultLayoutType(domain.getDefaultLayoutType());
        draft.setDefaultTableMode(domain.getDefaultTableMode());
        return draft;
    }

    LowcodeAiDomainDraftDTO defaultDomainDraft(String description) {
        return buildNewDomainDraft(inferDomainCandidates(description).get(0), description);
    }

    void applySingleDomainPreference(LowcodeAiAppGenerateResult result,
                                     AiLowcodeDomain selectedDomain,
                                     LowcodeAiAppGenerateRequest request) {
        if (selectedDomain != null || !isSingleDomainRequested(request.getDescription())
            || result.getDomains().size() <= 1) {
            return;
        }
        LowcodeAiDomainDraftDTO primaryDomain = choosePrimaryDomainDraft(result, request.getDescription());
        primaryDomain.setObjectCodes(resolveGeneratedObjectCodes(result));
        result.getDomains().clear();
        result.getDomains().add(primaryDomain);
        result.getDecisions().add(decision(
            "domain", "业务领域合并", primaryDomain.getDomainName(), "单一业务领域",
            "用户明确要求不要拆分多个业务领域，已将模型和应用统一归属到同一领域。",
            Map.of("singleDomain", true, "domainCode", primaryDomain.getDomainCode())));
    }

    void applyRequestedTablePrefix(LowcodeAiAppGenerateResult result, String description) {
        String tablePrefix = parseRequestedTablePrefix(description);
        if (StringUtils.isBlank(tablePrefix)) {
            return;
        }
        applyTablePrefixToDomains(result.getDomains(), tablePrefix);
        result.getDecisions().add(decision(
            "model", "表名前缀", "数据模型", tablePrefix,
            "用户明确要求数据表使用该前缀，已在模型协议归一化时强制应用。",
            Map.of("tablePrefix", tablePrefix)));
    }

    void applyRequestedTablePrefixToDomains(List<LowcodeAiDomainDraftDTO> domains, String description) {
        String tablePrefix = parseRequestedTablePrefix(description);
        if (StringUtils.isNotBlank(tablePrefix)) {
            applyTablePrefixToDomains(domains, tablePrefix);
        }
    }

    void normalizeDomainDraft(LowcodeAiDomainDraftDTO domainDraft, String description) {
        if (StringUtils.isBlank(domainDraft.getDomainCode())) {
            domainDraft.setDomainCode("biz_ops");
        }
        domainDraft.setDomainCode(normalizeCode(domainDraft.getDomainCode(), "biz_ops", 48));
        domainDraft.setDomainName(StringUtils.defaultIfBlank(domainDraft.getDomainName(), "业务运营"));
        domainDraft.setDomainDesc(StringUtils.defaultIfBlank(domainDraft.getDomainDesc(), description));
        domainDraft.setStatus(StringUtils.defaultIfBlank(domainDraft.getStatus(), STATUS_ENABLED));
        domainDraft.setSort(domainDraft.getSort() == null ? 0 : domainDraft.getSort());
        domainDraft.setTablePrefix(normalizePrefix(StringUtils.defaultIfBlank(
            domainDraft.getTablePrefix(), "biz_" + domainDraft.getDomainCode() + "_")));
        domainDraft.setConfigKeyPrefix(normalizePrefix(StringUtils.defaultIfBlank(
            domainDraft.getConfigKeyPrefix(), domainDraft.getDomainCode() + "_")));
        domainDraft.setDefaultAppType(StringUtils.defaultIfBlank(domainDraft.getDefaultAppType(), "SINGLE"));
        domainDraft.setDefaultLayoutType(StringUtils.defaultIfBlank(
            domainDraft.getDefaultLayoutType(), SIMPLE_LAYOUT));
        domainDraft.setDefaultTableMode(StringUtils.defaultIfBlank(
            domainDraft.getDefaultTableMode(), "CREATE"));
        if (domainDraft.getDomainSchema() == null) {
            domainDraft.setDomainSchema(buildDomainSchema(domainDraft, description));
        }
    }

    LowcodeDomainSchema buildDomainSchema(LowcodeAiDomainDraftDTO draft, String description) {
        LowcodeDomainSchema schema = new LowcodeDomainSchema();
        schema.getAiContext().setDescription(description);
        schema.getAiContext().setTerms(new ArrayList<>(new LinkedHashSet<>(safeList(draft.getObjectCodes()))));
        schema.getAiContext().setCommonObjects(new ArrayList<>(safeList(draft.getObjectCodes())));
        schema.getAiContext().setGenerationNotes(List.of("由 AI 业务系统生成 Agent 自动规划，用户确认后保存"));
        schema.getNaming().setTablePrefix(draft.getTablePrefix());
        schema.getNaming().setConfigKeyPrefix(draft.getConfigKeyPrefix());
        schema.getNaming().setObjectCodeStyle("lower_snake");
        schema.getDefaults().setAppType("SINGLE");
        schema.getDefaults().setLayoutType(SIMPLE_LAYOUT);
        schema.getDefaults().setTableMode("CREATE");
        schema.getDefaults().setMenuParentId(draft.getMenuParentId());
        schema.getCodegen().setModuleName(draft.getDomainCode());
        return schema;
    }

    String normalizeCode(String value, String fallback, int maxLength) {
        String normalized = StringUtils.defaultString(value)
            .replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5]+", "_")
            .replaceAll("[\\u4e00-\\u9fa5]+", fallback)
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("_+", "_")
            .toLowerCase(Locale.ROOT)
            .replaceAll("^[^a-z]+", "")
            .replaceAll("_+$", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = fallback;
        }
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength).replaceAll("_+$", "");
        }
        return normalized;
    }

    String normalizePrefix(String prefix) {
        String value = normalizeCode(prefix, "biz", 48);
        return value.endsWith("_") ? value : value + "_";
    }

    String parseRequestedTablePrefix(String description) {
        String text = StringUtils.defaultString(description);
        Matcher matcher = TABLE_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return normalizePrefix(matcher.group(1));
        }
        matcher = TABLE_PREFIX_FALLBACK_PATTERN.matcher(text);
        return matcher.find() ? normalizePrefix(matcher.group(1)) : null;
    }

    String normalizeTableName(String value) {
        String normalized = StringUtils.defaultString(value)
            .replaceAll("[^A-Za-z0-9_]+", "_")
            .replaceAll("_+", "_")
            .toLowerCase(Locale.ROOT)
            .replaceAll("^[^a-z]+", "")
            .replaceAll("_+$", "");
        return StringUtils.defaultIfBlank(normalized, "biz_ai_model");
    }

    String normalizeConfigKey(String value) {
        return normalizeCode(value, "ai_app", 64);
    }

    boolean shouldUseTree(String description, String objectName) {
        String text = StringUtils.defaultString(description) + StringUtils.defaultString(objectName);
        return containsAny(text, Set.of("树", "树形", "组织架构", "部门", "分类", "类目", "目录", "父级", "上级"));
    }

    boolean containsAny(String text, Set<String> keywords) {
        if (StringUtils.isBlank(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream().anyMatch(keyword -> StringUtils.isNotBlank(keyword) && text.contains(keyword));
    }

    private List<DomainCandidate> inferDomainCandidates(String description) {
        String text = StringUtils.defaultString(description);
        List<DomainCandidate> candidates = DOMAIN_CANDIDATES.stream()
            .filter(candidate -> containsAny(text, candidate.keywords()))
            .toList();
        return candidates.isEmpty()
            ? List.of(new DomainCandidate("biz_ops", "业务运营", "通用业务运营领域", Set.of()))
            : candidates;
    }

    private DomainCandidate findDomainCandidate(String domainCode) {
        return DOMAIN_CANDIDATES.stream()
            .filter(candidate -> candidate.code().equals(domainCode))
            .findFirst()
            .orElse(new DomainCandidate(domainCode, "业务运营", "通用业务运营领域", Set.of()));
    }

    private LowcodeAiDomainDraftDTO buildResolvedDomainDraft(DomainCandidate candidate, String description) {
        AiLowcodeDomain existing = domainService.getByCode(candidate.code());
        if (existing != null && STATUS_ENABLED.equals(existing.getStatus())) {
            return domainDraftFromEntity(existing);
        }
        String domainCode = existing == null
            ? candidate.code() : nextAvailableDomainCode(candidate.code() + "_ai");
        return buildNewDomainDraft(new DomainCandidate(
            domainCode, candidate.name(), candidate.description(), candidate.keywords()), description);
    }

    private LowcodeAiDomainDraftDTO choosePrimaryDomainDraft(LowcodeAiAppGenerateResult result,
                                                              String description) {
        String preferredDomainCode = null;
        if (!result.getModels().isEmpty()) {
            LowcodeModelSchema schema = result.getModels().get(0).getModelSchema();
            if (schema != null && schema.getDomain() != null) {
                preferredDomainCode = schema.getDomain().getCode();
            }
        }
        if (StringUtils.isBlank(preferredDomainCode) && !result.getApps().isEmpty()) {
            preferredDomainCode = result.getApps().get(0).getDomainCode();
        }
        if (StringUtils.isNotBlank(preferredDomainCode)) {
            for (LowcodeAiDomainDraftDTO domain : result.getDomains()) {
                if (preferredDomainCode.equals(domain.getDomainCode())) {
                    return domain;
                }
            }
        }
        return result.getDomains().isEmpty() ? defaultDomainDraft(description) : result.getDomains().get(0);
    }

    private List<String> resolveGeneratedObjectCodes(LowcodeAiAppGenerateResult result) {
        LinkedHashSet<String> objectCodes = new LinkedHashSet<>();
        for (var model : safeList(result.getModels())) {
            if (model != null && StringUtils.isNotBlank(model.getModelCode())) {
                objectCodes.add(model.getModelCode());
            }
        }
        for (var app : safeList(result.getApps())) {
            if (app != null && StringUtils.isNotBlank(app.getObjectCode())) {
                objectCodes.add(app.getObjectCode());
            }
        }
        return new ArrayList<>(objectCodes);
    }

    private void applyTablePrefixToDomains(List<LowcodeAiDomainDraftDTO> domains, String tablePrefix) {
        for (LowcodeAiDomainDraftDTO domain : safeList(domains)) {
            domain.setTablePrefix(tablePrefix);
            syncDomainSchemaNaming(domain);
        }
    }

    private void syncDomainSchemaNaming(LowcodeAiDomainDraftDTO domain) {
        if (domain == null || domain.getDomainSchema() == null) {
            return;
        }
        domain.getDomainSchema().getNaming().setTablePrefix(domain.getTablePrefix());
        domain.getDomainSchema().getNaming().setConfigKeyPrefix(domain.getConfigKeyPrefix());
        domain.getDomainSchema().getCodegen().setModuleName(domain.getDomainCode());
    }

    private boolean isSingleDomainRequested(String description) {
        String text = StringUtils.defaultString(description);
        return text.contains("一个业务领域") || text.contains("同一个业务领域")
            || text.contains("不要分开") || text.contains("不要拆分")
            || text.contains("不分开") || text.contains("不拆分");
    }

    private String nextAvailableDomainCode(String baseCode) {
        String normalized = normalizeCode(baseCode, "biz_ops", 44);
        String candidate = normalized;
        int index = 1;
        while (domainService.getByCode(candidate) != null) {
            candidate = normalizeCode(normalized + "_" + index, "biz_ops", 48);
            index++;
        }
        return candidate;
    }

    private LowcodeAiDomainDraftDTO buildNewDomainDraft(DomainCandidate candidate, String description) {
        LowcodeAiDomainDraftDTO draft = new LowcodeAiDomainDraftDTO();
        draft.setDomainCode(candidate.code());
        draft.setDomainName(candidate.name());
        draft.setDomainDesc(candidate.description());
        draft.setIcon("apps");
        draft.setSort(0);
        draft.setStatus(STATUS_ENABLED);
        draft.setTablePrefix(normalizePrefix("biz_" + candidate.code()));
        draft.setConfigKeyPrefix(normalizePrefix(candidate.code()));
        draft.setDefaultAppType("SINGLE");
        draft.setDefaultLayoutType(SIMPLE_LAYOUT);
        draft.setDefaultTableMode("CREATE");
        draft.setDomainSchema(buildDomainSchema(draft, description));
        return draft;
    }

    private String inferObjectName(String description) {
        String text = StringUtils.defaultString(description).trim();
        if (text.length() > 18) {
            text = text.substring(0, 18);
        }
        text = text.replaceAll("[，。,.\\s].*$", "");
        return StringUtils.defaultIfBlank(text, "业务对象");
    }

    private LowcodeAiDecisionDTO decision(String type, String title, String target, String value,
                                           String reason, Map<String, Object> meta) {
        LowcodeAiDecisionDTO decision = new LowcodeAiDecisionDTO();
        decision.setDecisionType(type);
        decision.setTitle(title);
        decision.setTarget(target);
        decision.setValue(value);
        decision.setReason(reason);
        decision.setMeta(new LinkedHashMap<>(meta));
        return decision;
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? new ArrayList<>() : source;
    }

    private record DomainCandidate(String code, String name, String description, Set<String> keywords) {
    }

    private record ObjectCandidate(String code, String name, String domainCode,
                                   Set<String> keywords, boolean tree) {
    }
}
