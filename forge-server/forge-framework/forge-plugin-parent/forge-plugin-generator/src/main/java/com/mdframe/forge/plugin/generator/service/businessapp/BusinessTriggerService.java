package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessTriggerExecutionStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerLogMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTriggerScenarioTemplateVO;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 触发器管理服务。
 * <p>
 * 负责触发器的 CRUD、启停操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessTriggerService {

    private final BusinessTriggerMapper triggerMapper;
    private final BusinessTriggerLogMapper triggerLogMapper;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.business.trigger-recovery.max-retry-count:5}")
    private int maxRetryCount = 5;

    @Value("${forge.business.trigger-recovery.lock-timeout-seconds:900}")
    private long lockTimeoutSeconds = 900;

    @Value("${forge.business.trigger-recovery.retry-base-seconds:30}")
    private long retryBaseSeconds = 30;

    /**
     * 分页查询触发器
     */
    public Page<AiBusinessTrigger> selectPage(String objectCode, PageQuery pageQuery) {
        return selectPage(objectCode, null, pageQuery);
    }

    /**
     * 分页查询触发器
     */
    public Page<AiBusinessTrigger> selectPage(String objectCode, String scenarioType, PageQuery pageQuery) {
        Long tenantId = resolveTenantId();
        Page<AiBusinessTrigger> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        return triggerMapper.selectTriggerPage(page, tenantId, StringUtils.trimToNull(objectCode),
                StringUtils.trimToNull(scenarioType));
    }

    /**
     * 查询触发器详情
     */
    public AiBusinessTrigger selectById(Long id) {
        return triggerMapper.selectById(id);
    }

    /**
     * 新增触发器
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiBusinessTrigger trigger) {
        validateTrigger(trigger);
        trigger.setTenantId(resolveTenantId());
        fillDefaults(trigger);
        trigger.setActionConfig(normalizeActionConfig(trigger.getActionType(), trigger.getActionConfig()));
        trigger.setExecuteCount(0L);
        triggerMapper.insert(trigger);
    }

    /**
     * 修改触发器
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(AiBusinessTrigger trigger) {
        validateTrigger(trigger);
        fillDefaults(trigger);
        trigger.setActionConfig(normalizeActionConfig(trigger.getActionType(), trigger.getActionConfig()));
        triggerMapper.updateById(trigger);
    }

    /**
     * 删除触发器
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        triggerMapper.deleteById(id);
    }

    /**
     * 启停触发器
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(id);
        trigger.setStatus(status);
        triggerMapper.updateById(trigger);
    }

    /**
     * 查询某对象某事件下所有启用的触发器
     */
    public List<AiBusinessTrigger> selectActiveByObjectAndEvent(Long tenantId, String objectCode, String eventType) {
        return triggerMapper.selectActiveByObjectAndEvent(tenantId, objectCode, eventType);
    }

    public List<AiBusinessTrigger> selectActiveScheduleTriggers(Integer limit) {
        int normalizedLimit = Math.min(Math.max(limit == null ? 100 : limit, 1), 500);
        return TenantContextHolder.executeIgnore(() -> triggerMapper.selectActiveScheduleTriggers(normalizedLimit));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean tryClaimExecution(AiBusinessTriggerLog logEntry) {
        validateExecutionLog(logEntry);
        if (logEntry.getId() == null) {
            logEntry.setId(IdWorker.getId());
        }
        boolean inserted;
        try {
            inserted = triggerLogMapper.insert(logEntry) == 1;
        } catch (DuplicateKeyException duplicate) {
            AiBusinessTriggerLog existing = selectByExecutionKey(logEntry);
            if (existing == null || !Objects.equals(existing.getEventDigest(), logEntry.getEventDigest())) {
                throw new BusinessException("业务事件ID与载荷摘要冲突");
            }
            return false;
        }
        return inserted && claim(logEntry, workerId, LocalDateTime.now()) != null;
    }

    public List<AiBusinessTriggerLog> findRecoveryCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        return TenantContextHolder.executeIgnore(() -> triggerLogMapper.selectRecoveryCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), safeBatchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessTriggerLog claimRecovery(AiBusinessTriggerLog candidate, String owner, LocalDateTime now) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getId() == null) {
            return null;
        }
        return claim(candidate, owner, now == null ? LocalDateTime.now() : now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markManualReview(AiBusinessTriggerLog claimed, String reason) {
        if (claimed == null) {
            return;
        }
        claimed.setExecuteStatus(BusinessTriggerExecutionStatus.TODO.getCode());
        claimed.setErrorMessage(StringUtils.abbreviate(
                StringUtils.defaultIfBlank(reason, "需要人工确认后重放"), 2000));
        claimed.setNextRetryTime(null);
        updateExecutionLog(claimed);
    }

    private AiBusinessTriggerLog claim(AiBusinessTriggerLog candidate, String owner, LocalDateTime now) {
        if (candidate == null || StringUtils.isBlank(owner)) {
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> triggerLogMapper.claimExecution(
                candidate.getTenantId(), candidate.getId(), owner, now,
                staleBefore(now), safeMaxRetryCount()));
        if (claimed != 1) {
            return null;
        }
        AiBusinessTriggerLog result = TenantContextHolder.executeIgnore(
                () -> triggerLogMapper.selectByLogId(candidate.getTenantId(), candidate.getId()));
        if (result != null) {
            copyClaimState(result, candidate);
        }
        return result;
    }

    private AiBusinessTriggerLog selectByExecutionKey(AiBusinessTriggerLog logEntry) {
        return TenantContextHolder.executeIgnore(() -> triggerLogMapper.selectByExecutionKey(
                logEntry.getTenantId(), logEntry.getTriggerId(), logEntry.getEventId()));
    }

    private void validateExecutionLog(AiBusinessTriggerLog logEntry) {
        if (logEntry == null || logEntry.getTenantId() == null || logEntry.getTenantId() <= 0
                || logEntry.getTriggerId() == null || StringUtils.isBlank(logEntry.getEventId())
                || StringUtils.isBlank(logEntry.getEventDigest())
                || StringUtils.isBlank(logEntry.getTriggerSnapshot())
                || StringUtils.isBlank(logEntry.getExecutionDigest())) {
            throw new BusinessException("业务事件缺少稳定事件ID");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateExecutionLog(AiBusinessTriggerLog logEntry) {
        if (logEntry == null || logEntry.getId() == null || logEntry.getTenantId() == null
                || StringUtils.isBlank(logEntry.getLockOwner())) {
            throw new BusinessException("业务触发器执行日志更新失败");
        }
        if (BusinessTriggerExecutionStatus.FAILED.matches(logEntry.getExecuteStatus())) {
            int attempts = logEntry.getRetryCount() == null ? 0 : logEntry.getRetryCount();
            if (attempts >= safeMaxRetryCount()) {
                logEntry.setExecuteStatus(BusinessTriggerExecutionStatus.DEAD.getCode());
                logEntry.setNextRetryTime(null);
            } else {
                logEntry.setNextRetryTime(LocalDateTime.now().plus(backoff(attempts)));
            }
        } else {
            logEntry.setNextRetryTime(null);
        }
        if (triggerLogMapper.updateExecutionResult(logEntry) != 1) {
            throw new BusinessException("业务触发器执行日志更新失败");
        }
    }

    private int safeMaxRetryCount() {
        return Math.max(1, maxRetryCount);
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minusSeconds(Math.max(30, lockTimeoutSeconds));
    }

    private Duration backoff(int attempts) {
        long baseSeconds = Math.max(1, retryBaseSeconds);
        int exponent = Math.max(0, Math.min(attempts - 1, 10));
        try {
            return Duration.ofSeconds(baseSeconds).multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            return Duration.ofHours(24);
        }
    }

    private void copyClaimState(AiBusinessTriggerLog claimed, AiBusinessTriggerLog target) {
        target.setId(claimed.getId());
        target.setRetryCount(claimed.getRetryCount());
        target.setLockOwner(claimed.getLockOwner());
        target.setLockTime(claimed.getLockTime());
        target.setExecuteTime(claimed.getExecuteTime());
    }

    /**
     * 更新触发器执行统计
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementExecuteCount(Long triggerId) {
        AiBusinessTrigger trigger = triggerMapper.selectById(triggerId);
        if (trigger != null) {
            trigger.setExecuteCount((trigger.getExecuteCount() == null ? 0L : trigger.getExecuteCount()) + 1);
            trigger.setLastExecuteTime(LocalDateTime.now());
            triggerMapper.updateById(trigger);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void touchScheduleScanTime(Long tenantId, Long triggerId) {
        if (tenantId == null || triggerId == null) {
            return;
        }
        TenantContextHolder.executeWithTenant(tenantId, () -> {
            AiBusinessTrigger trigger = new AiBusinessTrigger();
            trigger.setId(triggerId);
            trigger.setLastExecuteTime(LocalDateTime.now());
            triggerMapper.updateById(trigger);
        });
    }

    /**
     * 查询触发器执行日志
     */
    public Page<AiBusinessTriggerLog> selectLogPage(Long triggerId, PageQuery pageQuery) {
        Long tenantId = resolveTenantId();
        Page<AiBusinessTriggerLog> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        return triggerLogMapper.selectTriggerLogPage(page, tenantId, triggerId);
    }

    public boolean hasSuccessOrTodoLogSince(Long tenantId, Long triggerId, String recordId,
                                            String eventType, LocalDateTime sinceTime) {
        if (tenantId == null || triggerId == null || StringUtils.isBlank(recordId)
                || StringUtils.isBlank(eventType) || sinceTime == null) {
            return false;
        }
        Long count = triggerLogMapper.countSuccessOrTodoSince(tenantId, triggerId, recordId, eventType, sinceTime);
        return count != null && count > 0;
    }

    public List<BusinessTriggerScenarioTemplateVO> scenarioTemplates() {
        List<BusinessTriggerScenarioTemplateVO> templates = new ArrayList<>();
        templates.add(template("RECORD_CREATED_START_FLOW", "新增记录后发起主流程",
                "记录创建后按条件自动发起主流程", BusinessEvent.RECORD_CREATED, "START_FLOW", null));
        templates.add(template("STATUS_CHANGED_SEND_MESSAGE", "状态变更后发送消息",
                "单据状态变化后发送站内消息", BusinessEvent.STATUS_CHANGED, "SEND_MESSAGE", "CREATOR"));
        templates.add(template("FLOW_APPROVED_CREATE_RECORD", "流程通过后创建记录",
                "流程通过后创建关联业务记录", BusinessEvent.FLOW_APPROVED, "CREATE_RECORD", null));
        templates.add(template("FIELD_CHANGED_UPDATE_FIELD", "字段变更后更新字段",
                "字段变化后更新当前或目标记录字段", BusinessEvent.FIELD_CHANGED, "UPDATE_FIELD", null));
        templates.add(template("DUE_DATE_REMINDER", "到期提醒",
                "按计划任务或到期字段生成提醒", "SCHEDULED_DUE", "SEND_MESSAGE", "OWNER"));
        return templates;
    }

    public String normalizeActionConfig(String actionType, String actionConfig) {
        JSONObject config = readJson(actionConfig, "动作配置");
        if ("START_FLOW".equals(actionType)) {
            return normalizeStartFlowActionConfig(config).toJSONString();
        }
        return config.toJSONString();
    }

    private JSONObject normalizeStartFlowActionConfig(JSONObject config) {
        JSONObject normalizedConfig = new JSONObject();
        boolean useMainFlow = config.getBoolean("useMainFlow") == null || config.getBooleanValue("useMainFlow");
        normalizedConfig.put("useMainFlow", useMainFlow);
        if (useMainFlow) {
            return normalizedConfig;
        }
        normalizedConfig.put("flowModelKey", StringUtils.trimToNull(config.getString("flowModelKey")));
        normalizedConfig.put("titleTemplate", StringUtils.trimToNull(config.getString("titleTemplate")));
        JSONArray normalized = new JSONArray();
        JSONArray mappings = config.getJSONArray("variableMapping");
        if (mappings != null) {
            for (int i = 0; i < mappings.size(); i++) {
                JSONObject item = mappings.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String formField = StringUtils.firstNonBlank(item.getString("formField"), item.getString("field"));
                String flowVariable = StringUtils.firstNonBlank(item.getString("flowVariable"), item.getString("variable"));
                if (StringUtils.isBlank(formField) || StringUtils.isBlank(flowVariable)) {
                    continue;
                }
                JSONObject mapping = new JSONObject();
                mapping.put("formField", formField.trim());
                mapping.put("flowVariable", flowVariable.trim());
                mapping.put("label", StringUtils.trimToNull(item.getString("label")));
                normalized.add(mapping);
            }
        }
        normalizedConfig.put("variableMapping", normalized);
        return normalizedConfig;
    }

    private void validateTrigger(AiBusinessTrigger trigger) {
        if (trigger == null) {
            throw new BusinessException("触发器不能为空");
        }
        if (StringUtils.isBlank(trigger.getObjectCode())) {
            throw new BusinessException("业务对象编码不能为空");
        }
        if (StringUtils.isBlank(trigger.getTriggerName())) {
            throw new BusinessException("触发器名称不能为空");
        }
        if (StringUtils.isBlank(trigger.getActionType())) {
            throw new BusinessException("动作类型不能为空");
        }
        if (StringUtils.isNotBlank(trigger.getEventCondition())) {
            readJson(trigger.getEventCondition(), "触发条件");
        }
        JSONObject actionConfig = readJson(trigger.getActionConfig(), "动作配置");
        if ("WEBHOOK".equals(StringUtils.upperCase(trigger.getActionType()))) {
            String failureStrategy = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    actionConfig.getString("failureStrategy"), "THROW"));
            BusinessActionStepDTO step = new BusinessActionStepDTO();
            step.setStepType("CALL_API");
            step.setRollbackOnFailure(!"LOG_AND_CONTINUE".equals(failureStrategy));
            step.setStepConfig(new java.util.LinkedHashMap<>(actionConfig));
            BusinessActionCommandPolicy.assertSafeConfiguration(actionConfig, "trigger.actionConfig.WEBHOOK");
            BusinessActionCommandPolicy.validateCallApiStep(step, step.getStepConfig());
        }
    }

    private void fillDefaults(AiBusinessTrigger trigger) {
        trigger.setTriggerType(normalizeTriggerType(trigger.getTriggerType()));
        trigger.setBlockingMode(StringUtils.defaultIfBlank(trigger.getBlockingMode(), "ASYNC"));
        if (trigger.getDeveloperMode() == null) {
            trigger.setDeveloperMode(0);
        }
        if (trigger.getStatus() == null) {
            trigger.setStatus(EnableStatus.ENABLED.getCode());
        }
        if (trigger.getSortOrder() == null) {
            trigger.setSortOrder(0);
        }
    }

    private String normalizeTriggerType(String triggerType) {
        String normalized = StringUtils.defaultIfBlank(triggerType, "EVENT").trim().toUpperCase();
        if ("SCHEDULED".equals(normalized)) {
            return "SCHEDULE";
        }
        return normalized;
    }

    private JSONObject readJson(String json, String label) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            throw new BusinessException(label + "不是有效JSON");
        }
    }

    private BusinessTriggerScenarioTemplateVO template(String scenarioType, String scenarioName, String description,
                                                       String eventType, String actionType, String receiverRule) {
        BusinessTriggerScenarioTemplateVO vo = new BusinessTriggerScenarioTemplateVO();
        vo.setScenarioType(scenarioType);
        vo.setScenarioName(scenarioName);
        vo.setDescription(description);
        vo.setEventType(eventType);
        vo.setActionType(actionType);
        vo.setReceiverRule(receiverRule);
        return vo;
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("缺少可信租户上下文");
        }
        return tenantId;
    }
}
