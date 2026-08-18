package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessActionExecutionLog;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionLogQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessActionExecutionLogMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionExecuteResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用业务动作执行服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessActionExecutionService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String CHILD_ROW_POSITION = "CHILD_ROW";
    private static final String CHILD_ROW_TYPE = "COMMAND";
    private static final String SAFE_RELATION_KEY_PATTERN = "^[A-Za-z][A-Za-z0-9_-]{0,127}$";

    private final ObjectMapper objectMapper;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessObjectActionService actionService;
    private final BusinessActionExecutionLogMapper logMapper;
    private final PlatformTransactionManager transactionManager;
    private final List<BusinessActionStepExecutor> stepExecutors;

    @org.springframework.beans.factory.annotation.Value("${forge.lowcode.audit.retention-years:7}")
    private int auditRetentionYears = 7;

    public BusinessActionExecuteResultVO execute(BusinessActionExecuteDTO dto) {
        return executeInternal(dto, null, null, true);
    }

    public BusinessActionExecuteResultVO executePublished(
            BusinessActionExecuteDTO dto,
            Integer publishedVersion) {
        if (publishedVersion == null || publishedVersion <= 0) {
            throw new BusinessException("已发布业务动作版本不能为空");
        }
        return executeInternal(dto, publishedVersion, null, true);
    }

    public BusinessActionExecuteResultVO executePublished(
            BusinessActionExecuteDTO dto,
            Integer publishedVersion,
            String capabilityRequestId) {
        if (StringUtils.isBlank(capabilityRequestId)
                || !capabilityRequestId.matches("^[A-Za-z0-9._:-]{1,64}$")) {
            throw new BusinessException("能力请求 ID 无效");
        }
        if (publishedVersion == null || publishedVersion <= 0) {
            throw new BusinessException("已发布业务动作版本不能为空");
        }
        return executeInternal(dto, publishedVersion, capabilityRequestId, true);
    }

    private BusinessActionExecuteResultVO executeInternal(
            BusinessActionExecuteDTO dto,
            Integer publishedVersion,
            String capabilityRequestId,
            boolean publishedOnly) {
        long startTime = System.currentTimeMillis();
        BusinessActionExecutionContext context = null;
        List<BusinessActionStepResultVO> stepResults = new ArrayList<>();
        AiBusinessActionExecutionLog logEntry = null;
        try {
            BusinessActionCommandPolicy.validateIdempotencyKey(dto == null ? null : dto.getIdempotencyKey());
            context = buildContext(dto, publishedVersion, capabilityRequestId, publishedOnly);
            AiBusinessActionExecutionLog reusableLog = findReusableLog(context);
            if (reusableLog != null) {
                return fromLog(reusableLog, true);
            }
            validateActionPermission(context.getAction());
            List<BusinessActionStepDTO> steps = resolveSteps(context.getAction());
            BusinessActionCommandPolicy.validateDefinition(context.getAction(), steps);
            String executionMode = BusinessActionCommandPolicy.resolveExecutionMode(context.getAction(), steps);
            context.setExecutionMode(executionMode);
            if (BusinessActionCommandPolicy.MODE_LOCAL_TRANSACTION.equals(executionMode)) {
                validateLocalTransactionTargets(context, steps);
            }
            logEntry = reserveLog(context, startTime);
            BusinessActionExecutionContext executionContext = context;

            StepExecutionOutcome outcome;
            if (BusinessActionCommandPolicy.MODE_LOCAL_TRANSACTION.equals(executionMode)) {
                outcome = new TransactionTemplate(transactionManager).execute(status -> {
                    try {
                        return executeSteps(executionContext, steps);
                    } catch (StepExecutionException e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });
            } else {
                outcome = executeSteps(executionContext, steps);
            }
            stepResults = outcome == null ? List.of() : outcome.stepResults();
            String executeStatus = hasTodo(stepResults) ? STATUS_TODO : hasFailed(stepResults) ? STATUS_FAILED : STATUS_SUCCESS;
            String message = resolveSuccessMessage(context.getAction(), executeStatus);
            applyAuditFields(logEntry, context);
            saveLog(logEntry, executeStatus, message, null, stepResults, startTime);
            return buildResult(context, logEntry.getId(), executeStatus, message, stepResults, startTime, false);
        } catch (IdempotentLogHitException e) {
            return fromLog(e.getLogEntry(), true);
        } catch (StepExecutionException e) {
            stepResults = e.getStepResults();
            String message = rootErrorMessage(e);
            saveFailureLog(context, logEntry, message, stepResults, startTime);
            throw new BusinessException(StringUtils.defaultIfBlank(message, "业务动作执行失败"));
        } catch (IdempotentConflictException e) {
            throw e;
        } catch (BusinessException e) {
            saveFailureLog(context, logEntry, e.getMessage(), stepResults, startTime);
            throw e;
        } catch (Exception e) {
            saveFailureLog(context, logEntry, e.getMessage(), stepResults, startTime);
            log.error("业务动作执行异常", e);
            throw new BusinessException(StringUtils.defaultIfBlank(e.getMessage(), "业务动作执行失败"));
        }
    }

    public BusinessActionExecuteResultVO preview(BusinessActionExecuteDTO dto) {
        BusinessActionExecutionContext context = buildContext(dto, null, null, false);
        validateActionPermission(context.getAction());
        List<BusinessActionStepResultVO> stepResults = resolveSteps(context.getAction()).stream()
                .map(step -> {
                    BusinessActionStepResultVO result = new BusinessActionStepResultVO();
                    result.setStepCode(step.getStepCode());
                    result.setStepName(step.getStepName());
                    result.setStepType(step.getStepType());
                    result.setStatus("PENDING");
                    result.setMessage("待执行");
                    return result;
                })
                .toList();
        BusinessActionExecuteResultVO result = buildResult(context, null, "PREVIEW", "动作预览通过", stepResults,
                System.currentTimeMillis(), false);
        result.setDurationMs(0L);
        return result;
    }

    public Page<AiBusinessActionExecutionLog> selectLogPage(BusinessActionLogQueryDTO query, PageQuery pageQuery) {
        PageQuery effective = pageQuery == null ? new PageQuery() : pageQuery;
        return logMapper.selectLogPage(new Page<>(effective.getPageNum(), effective.getPageSize()),
                resolveTenantId(), query == null ? new BusinessActionLogQueryDTO() : query);
    }

    private BusinessActionExecutionContext buildContext(
            BusinessActionExecuteDTO dto,
            Integer publishedVersion,
            String capabilityRequestId,
            boolean publishedOnly) {
        if (dto == null) {
            throw new BusinessException("动作执行参数不能为空");
        }
        String objectCode = resolveObjectCode(dto);
        if (StringUtils.isBlank(objectCode)) {
            throw new BusinessException("业务对象编码不能为空");
        }
        dto.setObjectCode(objectCode);
        if (StringUtils.isBlank(dto.getActionCode())) {
            throw new BusinessException("动作编码不能为空");
        }
        AiBusinessObject object;
        BusinessObjectActionVO resolvedAction;
        AiBusinessObjectDesignVersion resolvedVersion = null;
        Integer resolvedPublishedVersion = publishedVersion;
        if (!publishedOnly) {
            BusinessObjectActionService.ResolvedBusinessAction resolved =
                    actionService.resolveAction(dto.getSuiteCode(), objectCode, dto.getActionCode());
            object = resolved.object();
            resolvedAction = resolved.action();
        }
        else {
            BusinessObjectActionService.ResolvedPublishedBusinessAction resolved =
                    actionService.resolvePublishedAction(
                            dto.getSuiteCode(), objectCode, dto.getActionCode(), publishedVersion);
            object = resolved.object();
            resolvedAction = resolved.action();
            resolvedVersion = resolved.version();
            Integer snapshotVersion = resolved.version() == null
                    ? null : resolved.version().getPublishVersion();
            resolvedPublishedVersion = snapshotVersion == null ? publishedVersion : snapshotVersion;
        }
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        if (object.getTenantId() == null || object.getTenantId() <= 0) {
            throw new BusinessException("业务对象缺少有效租户");
        }
        context.setTenantId(object.getTenantId());
        context.setCorrelationId(UUID.randomUUID().toString().replace("-", ""));
        context.setBusinessObject(object);
        context.setAction(resolvedAction);
        context.setPublishedVersion(resolvedPublishedVersion);
        if (StringUtils.isNotBlank(capabilityRequestId)) {
            var identity = ExecutionIdentityContextHolder.current()
                    .orElseThrow(() -> new BusinessException("受控业务动作缺少可信执行身份"));
            context.setCapabilityRequestId(capabilityRequestId);
            context.setCapabilityClientId(identity.clientId());
            context.setCapabilityServiceUserId(identity.serviceUserId());
            context.setCapabilityActorType(identity.actorType());
        }
        context.setRequest(dto);
        context.setFormData(BusinessActionCommandPolicy.projectFormData(resolvedAction, dto.getFormData()));
        context.setExtraContext(BusinessActionCommandPolicy.sanitizeClientContext(dto.getContext()));
        if (isChildRowAction(resolvedAction)) {
            populateChildRowContext(context, resolvedVersion, publishedOnly);
        } else if (StringUtils.isNotBlank(dto.getRecordId()) && StringUtils.isNotBlank(object.getConfigKey())) {
            Map<String, Object> detail = dynamicCrudService.selectById(object.getConfigKey(), dto.getRecordId());
            if (detail != null) {
                // selectById 返回 { main: {...}, children: {...} }，recordData 只取 main 部分，
                // 否则 targetRecordIdField='record.id' 在根找不到 id（id 在 main 内部）。
                Map<String, Object> main = asStringMap(detail.get("main"));
                context.setRecordData(main.isEmpty() ? new LinkedHashMap<>(detail) : new LinkedHashMap<>(main));
            } else {
                context.setRecordData(new LinkedHashMap<>());
            }
        }
        context.setSystemContext(buildSystemContext(context));
        return context;
    }

    private boolean isChildRowAction(BusinessObjectActionVO action) {
        return action != null && CHILD_ROW_POSITION.equalsIgnoreCase(
                StringUtils.defaultString(action.getActionPosition()));
    }

    private void populateChildRowContext(
            BusinessActionExecutionContext context,
            AiBusinessObjectDesignVersion version,
            boolean publishedOnly) {
        BusinessActionExecuteDTO request = context.getRequest();
        BusinessObjectActionVO action = context.getAction();
        if (!CHILD_ROW_TYPE.equalsIgnoreCase(StringUtils.defaultString(action.getActionType()))) {
            throw new BusinessException("子表行动作仅支持事务型业务命令");
        }
        String parentRecordId = StringUtils.trimToNull(request.getParentRecordId());
        String childRecordId = StringUtils.trimToNull(request.getChildRecordId());
        String relationKey = StringUtils.trimToNull(request.getRelationKey());
        if (parentRecordId == null || childRecordId == null || relationKey == null) {
            throw new BusinessException("子表行动作缺少父记录、子记录或关系标识");
        }
        if (!relationKey.matches(SAFE_RELATION_KEY_PATTERN)) {
            throw new BusinessException("子表行动作关系标识无效");
        }
        if (StringUtils.isNotBlank(request.getRecordId())
                && !StringUtils.equals(request.getRecordId().trim(), childRecordId)) {
            throw new BusinessException("子表行动作记录标识不一致");
        }
        String actionRelationKey = BusinessActionStepConfigHelper.firstText(
                action.getActionConfig(), "relationKey");
        if (!StringUtils.equals(actionRelationKey, relationKey)) {
            throw new BusinessException("子表行动作与关系配置不一致");
        }
        ChildRelationSnapshot relation = publishedOnly
                ? resolvePublishedChildRelation(context, version, relationKey)
                : new ChildRelationSnapshot(relationKey, null);
        AiBusinessObject object = context.getBusinessObject();
        if (StringUtils.isBlank(object.getConfigKey())) {
            throw new BusinessException("子表行动作所属对象缺少运行配置");
        }
        Map<String, Object> detail = dynamicCrudService.selectById(object.getConfigKey(), parentRecordId);
        if (detail == null || detail.isEmpty()) {
            throw new BusinessException("父记录不存在或无权访问");
        }
        Map<String, Object> parentRecord = asStringMap(detail.get("main"));
        Map<String, Object> children = asStringMap(detail.get("children"));
        if (parentRecord.isEmpty() || children.isEmpty()) {
            throw new BusinessException("父记录不包含可执行的子表关系");
        }
        Object rawRows = children.get(relation.key());
        if (!(rawRows instanceof List<?>) && StringUtils.isNotBlank(relation.targetObjectCode())) {
            rawRows = children.get(relation.targetObjectCode());
        }
        if (!(rawRows instanceof List<?>) && StringUtils.isNotBlank(relation.targetObjectCode())) {
            rawRows = children.get(StringUtils.lowerCase(relation.targetObjectCode()));
        }
        if (!(rawRows instanceof List<?>)) {
            throw new BusinessException("发布关系未生成对应的子表运行配置（relationKey="
                    + relation.key() + ", targetObjectCode=" + relation.targetObjectCode()
                    + ", childrenKeys=" + children.keySet() + "）");
        }
        List<?> rows = (List<?>) rawRows;
        Map<String, Object> childRecord = rows.stream()
                .map(this::asStringMap)
                .filter(row -> sameRecordId(row.get("id"), childRecordId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("子记录不存在或不属于当前父记录"));
        request.setRecordId(childRecordId);
        context.setParentRecordData(new LinkedHashMap<>(parentRecord));
        context.setRecordData(new LinkedHashMap<>(childRecord));
    }

    private ChildRelationSnapshot resolvePublishedChildRelation(
            BusinessActionExecutionContext context,
            AiBusinessObjectDesignVersion version,
            String relationKey) {
        if (version == null || StringUtils.isBlank(version.getRelationSnapshot())) {
            throw new BusinessException("发布版本缺少子表关系快照");
        }
        List<Map<String, Object>> relations;
        try {
            relations = objectMapper.readValue(version.getRelationSnapshot(), new TypeReference<>() { });
        } catch (Exception e) {
            throw new BusinessException("发布版本子表关系快照无效");
        }
        String sourceObjectCode = context.getBusinessObject().getObjectCode();
        for (Map<String, Object> relation : relations) {
            if (relation == null
                    || !sourceObjectCode.equals(StringUtils.trimToEmpty(text(relation.get("sourceObjectCode"))))
                    || intValue(relation.get("status"), 1) == 0) {
                continue;
            }
            String relationType = StringUtils.upperCase(StringUtils.defaultString(text(relation.get("relationType"))));
            if (!Set.of("DETAIL", "CHILD_LIST", "ONE_TO_MANY").contains(relationType)) {
                continue;
            }
            String targetObjectCode = text(relation.get("targetObjectCode"));
            Map<String, Object> relationConfig = readRelationConfig(relation.get("relationConfig"));
            String snapshotKey = StringUtils.defaultIfBlank(
                    text(relationConfig.get("relationKey")), defaultRelationKey(targetObjectCode));
            if (relationKey.equals(snapshotKey)) {
                return new ChildRelationSnapshot(snapshotKey, targetObjectCode);
            }
        }
        throw new BusinessException("发布版本中不存在匹配的子表关系");
    }

    private Map<String, Object> readRelationConfig(Object value) {
        if (value instanceof Map<?, ?>) {
            return asStringMap(value);
        }
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() { });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private boolean sameRecordId(Object actual, String expected) {
        return actual != null && StringUtils.equals(String.valueOf(actual), expected);
    }

    private String text(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String defaultRelationKey(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
    }

    String resolveObjectCode(BusinessActionExecuteDTO dto) {
        if (dto == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(dto.getObjectCode()),
                StringUtils.trimToNull(dto.getBusinessObjectCode()),
                StringUtils.trimToNull(dto.getTargetObjectCode()),
                StringUtils.trimToNull(dto.getTargetEntityCode()),
                StringUtils.trimToNull(dto.getCandidateObjectCode()),
                StringUtils.trimToNull(dto.getReferenceObjectCode()),
                StringUtils.trimToNull(dto.getRefObjectCode()),
                StringUtils.trimToNull(dto.getSourceObjectCode()),
                StringUtils.trimToNull(dto.getTargetCode()));
    }

    private Map<String, Object> buildSystemContext(BusinessActionExecutionContext context) {
        Map<String, Object> system = new LinkedHashMap<>();
        LoginUser user;
        try {
            user = SessionHelper.getLoginUser();
        } catch (Exception ignored) {
            user = null;
        }
        system.put("tenantId", context.getTenantId());
        system.put("correlationId", context.getCorrelationId());
        system.put("recordId", context.getRequest() == null ? null : context.getRequest().getRecordId());
        system.put("parentRecordId", context.getRequest() == null ? null : context.getRequest().getParentRecordId());
        system.put("childRecordId", context.getRequest() == null ? null : context.getRequest().getChildRecordId());
        system.put("relationKey", context.getRequest() == null ? null : context.getRequest().getRelationKey());
        system.put("objectCode", context.getBusinessObject() == null ? null : context.getBusinessObject().getObjectCode());
        if (user != null) {
            system.put("userId", user.getUserId());
            system.put("username", user.getUsername());
            system.put("realName", user.getRealName());
            system.put("activeOrgId", user.getActiveOrgId());
            system.put("activeOrgName", user.getActiveOrgName());
            system.put("mainOrgId", user.getMainOrgId());
        }
        return system;
    }

    private void validateLocalTransactionTargets(
            BusinessActionExecutionContext context,
            List<BusinessActionStepDTO> steps) {
        for (BusinessActionStepDTO step : steps) {
            if (step == null) {
                continue;
            }
            String type = StringUtils.defaultString(step.getStepType()).toUpperCase(Locale.ROOT);
            if ("FOREACH".equals(type)) {
                Map<String, Object> config = step.getStepConfig();
                Object nested = config == null ? null : config.get("steps");
                if (!(nested instanceof List<?>)) {
                    nested = config == null ? null : config.get("stepList");
                }
                validateLocalTransactionTargets(context, normalizeNestedSteps(nested));
                continue;
            }
            Map<String, Object> config = step.getStepConfig();
            String targetConfigKey = BusinessActionStepConfigHelper.firstText(config, "targetConfigKey");
            if (StringUtils.isBlank(targetConfigKey) && !"CREATE_RECORD".equals(type)) {
                targetConfigKey = context.getBusinessObject() == null
                        ? null : context.getBusinessObject().getConfigKey();
            }
            if (StringUtils.isBlank(targetConfigKey)) {
                throw new BusinessException("本地事务步骤缺少 targetConfigKey: " + type);
            }
            dynamicCrudService.assertLocalTransactionConfig(targetConfigKey);
        }
    }

    List<BusinessActionStepResultVO> executeNestedSteps(BusinessActionExecutionContext context, List<BusinessActionStepDTO> steps) {
        StepExecutionOutcome outcome = executeSteps(context, steps);
        return outcome == null ? List.of() : outcome.stepResults();
    }

    private StepExecutionOutcome executeSteps(BusinessActionExecutionContext context, List<BusinessActionStepDTO> steps) {
        Map<String, BusinessActionStepExecutor> executors = stepExecutors.stream()
                .collect(Collectors.toMap(item -> item.supportType().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<BusinessActionStepResultVO> results = new ArrayList<>();
        for (BusinessActionStepDTO step : steps) {
            long startTime = System.currentTimeMillis();
            BusinessActionStepResultVO result = null;
            try {
                String stepType = StringUtils.defaultString(step.getStepType()).toUpperCase(Locale.ROOT);
                BusinessActionStepExecutor executor = executors.get(stepType);
                if (executor == null) {
                    throw new BusinessException("不支持的动作步骤类型: " + step.getStepType());
                }
                result = executor.execute(context, step);
                normalizeStepResult(result, step, startTime);
                results.add(result);
            } catch (Exception e) {
                result = failedStepResult(step, e, startTime);
                results.add(result);
                if (!Boolean.FALSE.equals(step.getRollbackOnFailure())) {
                    throw new StepExecutionException("动作步骤执行失败: " + step.getStepName(), e, results);
                }
            }
        }
        return new StepExecutionOutcome(results);
    }

    private List<BusinessActionStepDTO> resolveSteps(BusinessObjectActionVO action) {
        Map<String, Object> actionConfig = action.getActionConfig() == null ? new LinkedHashMap<>() : action.getActionConfig();
        Object rawSteps = actionConfig.get("steps");
        if (!(rawSteps instanceof List<?> list)) {
            rawSteps = actionConfig.get("stepList");
        }
        return normalizeSteps(rawSteps, "业务动作未配置执行步骤", "业务动作未配置有效执行步骤");
    }

    List<BusinessActionStepDTO> normalizeNestedSteps(Object rawSteps) {
        return normalizeSteps(rawSteps, "循环动作未配置子步骤", "循环动作未配置有效子步骤");
    }

    private List<BusinessActionStepDTO> normalizeSteps(Object rawSteps, String missingMessage, String emptyMessage) {
        if (!(rawSteps instanceof List<?> list)) {
            throw new BusinessException(missingMessage);
        }
        List<BusinessActionStepDTO> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            BusinessActionStepDTO step = objectMapper.convertValue(item, BusinessActionStepDTO.class);
            step.setStepCode(StringUtils.defaultIfBlank(step.getStepCode(), "step_" + (i + 1)));
            step.setStepName(StringUtils.defaultIfBlank(step.getStepName(), step.getStepCode()));
            step.setStepType(normalizeStepType(step.getStepType()));
            step.setSortOrder(step.getSortOrder() == null ? i * 10 + 10 : step.getSortOrder());
            if (step.getStepConfig() == null) {
                step.setStepConfig(new LinkedHashMap<>());
            }
            steps.add(step);
        }
        if (steps.isEmpty()) {
            throw new BusinessException(emptyMessage);
        }
        steps.sort(Comparator.comparing(step -> step.getSortOrder() == null ? Integer.MAX_VALUE : step.getSortOrder()));
        return steps;
    }

    private String normalizeStepType(String stepType) {
        String normalized = StringUtils.defaultString(stepType)
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace("-", "_")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            throw new BusinessException("动作步骤类型不能为空");
        }
        return normalized;
    }

    private void validateActionPermission(BusinessObjectActionVO action) {
        String permission = StringUtils.trimToNull(action.getPermission());
        if (permission == null) {
            return;
        }
        boolean granted;
        try {
            granted = SessionHelper.hasPermission(permission);
        } catch (Exception e) {
            granted = false;
        }
        if (!granted) {
            throw new BusinessException("无权限执行动作: " + permission);
        }
    }

    private AiBusinessActionExecutionLog findReusableLog(BusinessActionExecutionContext context) {
        String idempotencyKey = StringUtils.trimToNull(context.getRequest().getIdempotencyKey());
        if (idempotencyKey == null) {
            return null;
        }
        AiBusinessActionExecutionLog existing = logMapper.selectLatestByIdempotencyKey(context.getTenantId(),
                context.getBusinessObject().getObjectCode(),
                context.getRequest().getRecordId(),
                context.getAction().getActionCode(),
                context.getPublishedVersion(),
                idempotencyKey);
        if (existing == null) {
            return null;
        }
        assertSameIdempotentRequest(existing, context);
        if (STATUS_RUNNING.equalsIgnoreCase(existing.getExecuteStatus())) {
            throw new IdempotentConflictException("业务动作正在执行，请稍候");
        }
        if (STATUS_SUCCESS.equalsIgnoreCase(existing.getExecuteStatus())
                || STATUS_TODO.equalsIgnoreCase(existing.getExecuteStatus())) {
            return existing;
        }
        if (STATUS_FAILED.equalsIgnoreCase(existing.getExecuteStatus())) {
            throw new IdempotentConflictException(StringUtils.defaultIfBlank(existing.getErrorMessage(),
                    StringUtils.defaultIfBlank(existing.getResultMessage(), "业务动作已执行失败，请更换幂等键后重试")));
        }
        return null;
    }

    private AiBusinessActionExecutionLog buildLogEntry(BusinessActionExecutionContext context, long startTime) {
        AiBusinessActionExecutionLog logEntry = new AiBusinessActionExecutionLog();
        logEntry.setTenantId(context.getTenantId());
        logEntry.setSuiteCode(context.getBusinessObject().getSuiteCode());
        logEntry.setObjectCode(context.getBusinessObject().getObjectCode());
        logEntry.setRecordId(StringUtils.trimToEmpty(context.getRequest().getRecordId()));
        logEntry.setActionCode(context.getAction().getActionCode());
        logEntry.setActionName(context.getAction().getActionName());
        logEntry.setRequestDigest(buildRequestDigest(context));
        logEntry.setCorrelationId(context.getCorrelationId());
        logEntry.setIdempotencyKey(StringUtils.trimToNull(context.getRequest().getIdempotencyKey()));
        logEntry.setActionVersion(context.getPublishedVersion());
        logEntry.setExecutionMode(context.getExecutionMode());
        logEntry.setDurationMs(System.currentTimeMillis() - startTime);
        logEntry.setCapabilityRequestId(context.getCapabilityRequestId());
        logEntry.setClientId(context.getCapabilityClientId());
        logEntry.setServiceUserId(context.getCapabilityServiceUserId());
        logEntry.setActorType(context.getCapabilityActorType());
        if (StringUtils.isBlank(logEntry.getActorType())) {
            logEntry.setActorType("SESSION");
        }
        int retentionYears = auditRetentionYears < 1 || auditRetentionYears > 30 ? 7 : auditRetentionYears;
        logEntry.setRetentionUntil(LocalDateTime.now().plusYears(retentionYears));
        return logEntry;
    }

    private void applyAuditFields(AiBusinessActionExecutionLog logEntry,
                                  BusinessActionExecutionContext context) {
        if (logEntry == null || context == null
                || context.getAuditTransitions() == null || context.getAuditTransitions().isEmpty()) {
            return;
        }
        List<Map<String, Object>> safeSummary = new ArrayList<>();
        for (Map<String, Object> transition : context.getAuditTransitions()) {
            if (transition == null) {
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            copyAuditText(safe, transition, "eventType");
            copyAuditText(safe, transition, "statusField");
            copyAuditText(safe, transition, "from");
            copyAuditText(safe, transition, "to");
            copyAuditText(safe, transition, "targetConfigKey");
            copyAuditText(safe, transition, "outcome");
            if (!safe.isEmpty()) {
                safeSummary.add(safe);
            }
        }
        if (safeSummary.isEmpty()) {
            return;
        }
        Map<String, Object> first = safeSummary.get(0);
        logEntry.setAuditEventType("STATUS_TRANSITION");
        logEntry.setStatusField(text(first.get("statusField")));
        logEntry.setStatusFrom(text(first.get("from")));
        logEntry.setStatusTo(text(first.get("to")));
        logEntry.setChangeSummary(StringUtils.left(writeJson(safeSummary), 4000));
    }

    private void copyAuditText(Map<String, Object> target, Map<String, Object> source, String key) {
        String value = StringUtils.trimToNull(text(source.get(key)));
        if (value != null) {
            target.put(key, StringUtils.left(value, 128));
        }
    }

    private AiBusinessActionExecutionLog reserveLog(BusinessActionExecutionContext context, long startTime) {
        AiBusinessActionExecutionLog logEntry = buildLogEntry(context, startTime);
        logEntry.setExecuteStatus(STATUS_RUNNING);
        logEntry.setResultMessage("动作执行中");
        logEntry.setDurationMs(0L);
        try {
            requiresNewTransaction().executeWithoutResult(status -> logMapper.insert(logEntry));
            return logEntry;
        } catch (DuplicateKeyException e) {
            AiBusinessActionExecutionLog existing = logMapper.selectLatestByIdempotencyKey(context.getTenantId(),
                    context.getBusinessObject().getObjectCode(),
                    context.getRequest().getRecordId(),
                    context.getAction().getActionCode(),
                    context.getPublishedVersion(),
                    context.getRequest().getIdempotencyKey());
            if (existing == null) {
                throw new IdempotentConflictException("业务动作重复提交，请稍候重试");
            }
            assertSameIdempotentRequest(existing, context);
            if (STATUS_SUCCESS.equalsIgnoreCase(existing.getExecuteStatus())
                    || STATUS_TODO.equalsIgnoreCase(existing.getExecuteStatus())) {
                throw new IdempotentLogHitException(existing);
            }
            if (STATUS_RUNNING.equalsIgnoreCase(existing.getExecuteStatus())) {
                throw new IdempotentConflictException("业务动作正在执行，请稍候");
            }
            throw new IdempotentConflictException(StringUtils.defaultIfBlank(existing.getErrorMessage(),
                    StringUtils.defaultIfBlank(existing.getResultMessage(), "业务动作已执行失败，请更换幂等键后重试")));
        }
    }

    private void saveFailureLog(BusinessActionExecutionContext context,
                                AiBusinessActionExecutionLog logEntry,
                                String errorMessage,
                                List<BusinessActionStepResultVO> stepResults,
                                long startTime) {
        if (context == null) {
            return;
        }
        AiBusinessActionExecutionLog actualLog = logEntry == null ? buildLogEntry(context, startTime) : logEntry;
        String message = resolveFailureMessage(context.getAction(), errorMessage);
        try {
            applyAuditFields(actualLog, context);
            saveLog(actualLog, STATUS_FAILED, message, errorMessage, stepResults, startTime);
        } catch (Exception logError) {
            log.warn("业务动作失败日志写入失败: objectCode={}, actionCode={}, error={}",
                    context.getBusinessObject() == null ? null : context.getBusinessObject().getObjectCode(),
                    context.getAction() == null ? null : context.getAction().getActionCode(),
                    logError.getMessage());
        }
    }

    private void saveLog(AiBusinessActionExecutionLog logEntry,
                         String status,
                         String message,
                         String errorMessage,
                         List<BusinessActionStepResultVO> stepResults,
                         long startTime) {
        if (logEntry == null) {
            return;
        }
        logEntry.setExecuteStatus(status);
        logEntry.setResultMessage(StringUtils.left(message, 500));
        logEntry.setErrorMessage(StringUtils.left(errorMessage, 2000));
        logEntry.setStepResult(writeJson(stepResults));
        logEntry.setDurationMs(System.currentTimeMillis() - startTime);
        requiresNewTransaction().executeWithoutResult(transactionStatus -> {
            if (logEntry.getId() == null) {
                logMapper.insert(logEntry);
            } else {
                logMapper.updateById(logEntry);
            }
        });
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private BusinessActionExecuteResultVO buildResult(BusinessActionExecutionContext context,
                                                      Long logId,
                                                      String executeStatus,
                                                      String message,
                                                      List<BusinessActionStepResultVO> stepResults,
                                                      long startTime,
                                                      boolean idempotentHit) {
        BusinessActionExecuteResultVO result = new BusinessActionExecuteResultVO();
        result.setLogId(logId);
        result.setSuiteCode(context.getBusinessObject().getSuiteCode());
        result.setObjectCode(context.getBusinessObject().getObjectCode());
        result.setRecordId(context.getRequest().getRecordId());
        result.setActionCode(context.getAction().getActionCode());
        result.setActionName(context.getAction().getActionName());
        result.setExecuteStatus(executeStatus);
        result.setMessage(message);
        result.setCorrelationId(context.getCorrelationId());
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setIdempotentHit(idempotentHit);
        result.setStepResults(stepResults == null ? new ArrayList<>() : stepResults);
        return result;
    }

    private BusinessActionExecuteResultVO fromLog(AiBusinessActionExecutionLog logEntry, boolean idempotentHit) {
        BusinessActionExecuteResultVO result = new BusinessActionExecuteResultVO();
        result.setLogId(logEntry.getId());
        result.setSuiteCode(logEntry.getSuiteCode());
        result.setObjectCode(logEntry.getObjectCode());
        result.setRecordId(logEntry.getRecordId());
        result.setActionCode(logEntry.getActionCode());
        result.setActionName(logEntry.getActionName());
        result.setExecuteStatus(logEntry.getExecuteStatus());
        result.setMessage(logEntry.getResultMessage());
        result.setCorrelationId(logEntry.getCorrelationId());
        result.setDurationMs(logEntry.getDurationMs());
        result.setIdempotentHit(idempotentHit);
        result.setStepResults(readStepResults(logEntry.getStepResult()));
        return result;
    }

    private void normalizeStepResult(BusinessActionStepResultVO result, BusinessActionStepDTO step, long startTime) {
        if (result == null) {
            throw new BusinessException("动作步骤没有返回执行结果: " + step.getStepCode());
        }
        result.setStepCode(StringUtils.defaultIfBlank(result.getStepCode(), step.getStepCode()));
        result.setStepName(StringUtils.defaultIfBlank(result.getStepName(), step.getStepName()));
        result.setStepType(StringUtils.defaultIfBlank(result.getStepType(), step.getStepType()));
        result.setStatus(StringUtils.defaultIfBlank(result.getStatus(), STATUS_SUCCESS));
        result.setDurationMs(System.currentTimeMillis() - startTime);
    }

    private BusinessActionStepResultVO failedStepResult(BusinessActionStepDTO step, Exception e, long startTime) {
        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStepCode(step.getStepCode());
        result.setStepName(step.getStepName());
        result.setStepType(step.getStepType());
        result.setStatus(STATUS_FAILED);
        result.setMessage("步骤执行失败");
        result.setErrorMessage(e.getMessage());
        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private String rootErrorMessage(Throwable error) {
        Throwable current = error;
        String message = null;
        while (current != null) {
            if (StringUtils.isNotBlank(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }

    private boolean hasTodo(List<BusinessActionStepResultVO> stepResults) {
        return stepResults != null && stepResults.stream().anyMatch(item -> STATUS_TODO.equalsIgnoreCase(item.getStatus()));
    }

    private boolean hasFailed(List<BusinessActionStepResultVO> stepResults) {
        return stepResults != null && stepResults.stream().anyMatch(item -> STATUS_FAILED.equalsIgnoreCase(item.getStatus()));
    }

    private String resolveSuccessMessage(BusinessObjectActionVO action, String executeStatus) {
        if (STATUS_TODO.equals(executeStatus)) {
            return "动作已记录为待处理";
        }
        return StringUtils.defaultIfBlank(action.getSuccessMessage(), "动作执行成功");
    }

    private String resolveFailureMessage(BusinessObjectActionVO action, String errorMessage) {
        return StringUtils.defaultIfBlank(action == null ? null : action.getFailureMessage(),
                StringUtils.defaultIfBlank(errorMessage, "动作执行失败"));
    }

    private String buildRequestDigest(BusinessActionExecutionContext context) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("suiteCode", context.getBusinessObject().getSuiteCode());
        digest.put("objectCode", context.getRequest().getObjectCode());
        digest.put("recordId", context.getRequest().getRecordId());
        digest.put("parentRecordId", context.getRequest().getParentRecordId());
        digest.put("childRecordId", context.getRequest().getChildRecordId());
        digest.put("relationKey", context.getRequest().getRelationKey());
        digest.put("actionCode", context.getRequest().getActionCode());
        digest.put("publishedVersion", context.getPublishedVersion());
        digest.put("formData", canonicalize(context.getFormData()));
        digest.put("context", canonicalize(context.getExtraContext()));
        return "sha256:" + sha256(writeJson(canonicalize(digest)));
    }

    private void assertSameIdempotentRequest(
            AiBusinessActionExecutionLog existing,
            BusinessActionExecutionContext context) {
        String currentDigest = buildRequestDigest(context);
        if (!currentDigest.equals(existing.getRequestDigest())) {
            throw new IdempotentConflictException("幂等键已被不同业务动作参数使用，请更换幂等键");
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonicalize(item)));
            return items;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                items.add(canonicalize(java.lang.reflect.Array.get(value, index)));
            }
            return items;
        }
        return value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<BusinessActionStepResultVO> readStepResults(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    private record StepExecutionOutcome(List<BusinessActionStepResultVO> stepResults) {
    }

    private record ChildRelationSnapshot(String key, String targetObjectCode) {
    }

    private static class StepExecutionException extends RuntimeException {
        private final List<BusinessActionStepResultVO> stepResults;

        StepExecutionException(String message, Throwable cause, List<BusinessActionStepResultVO> stepResults) {
            super(message, cause);
            this.stepResults = new ArrayList<>(stepResults);
        }

        List<BusinessActionStepResultVO> getStepResults() {
            return stepResults;
        }
    }

    private static class IdempotentLogHitException extends RuntimeException {
        private final AiBusinessActionExecutionLog logEntry;

        IdempotentLogHitException(AiBusinessActionExecutionLog logEntry) {
            super("业务动作幂等命中");
            this.logEntry = logEntry;
        }

        AiBusinessActionExecutionLog getLogEntry() {
            return logEntry;
        }
    }

    private static class IdempotentConflictException extends BusinessException {

        IdempotentConflictException(String message) {
            super(message);
        }
    }
}
