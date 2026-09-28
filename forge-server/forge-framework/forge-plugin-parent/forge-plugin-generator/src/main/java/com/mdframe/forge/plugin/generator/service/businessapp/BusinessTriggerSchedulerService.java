package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.job.annotation.ScheduledJob;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 业务定时触发器扫描任务。
 * <p>
 * 注册到系统任务调度中心，使用一个平台任务控制频率和批量，不为每条业务触发器创建秒级任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business-trigger.schedule", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessTriggerSchedulerService {

    private static final String SCAN_LOCK_KEY = "forge:business-trigger:schedule:scan";
    private static final String RECORD_LOCK_PREFIX = "forge:business-trigger:schedule:record:";
    private static final int DEFAULT_TRIGGER_LIMIT = 100;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 200;
    private static final int DEFAULT_MIN_INTERVAL_MINUTES = 5;

    private final BusinessTriggerService triggerService;
    private final BusinessTriggerExecutor triggerExecutor;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessAppMapper businessAppMapper;
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    private volatile boolean localLockWarningLogged;

    @Value("${forge.business-trigger.schedule.max-triggers-per-run:100}")
    private Integer maxTriggersPerRun;

    @Value("${forge.business-trigger.schedule.cluster-lock-enabled:true}")
    private Boolean clusterLockEnabled;

    @Value("${forge.business-trigger.schedule.lock-wait-ms:0}")
    private Long lockWaitMs;

    @ScheduledJob(
            name = "lowcodeBusinessTriggerScanJob",
            group = "LOWCODE",
            cron = "0 0/5 * * * ?",
            description = "低代码定时触发器扫描任务，默认每5分钟执行一次"
    )
    public String scanScheduledTriggers() {
        if (!Boolean.TRUE.equals(clusterLockEnabled)) {
            doScanScheduledTriggers();
            return "SUCCESS";
        }

        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            logLocalLockWarning();
            doScanScheduledTriggers();
            return "SUCCESS";
        }

        RLock lock = redissonClient.getLock(SCAN_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(normalizeLockWaitMs(), TimeUnit.MILLISECONDS);
            if (!locked) {
                log.debug("[定时触发] 集群扫描锁已被其他节点持有，本轮跳过");
                return "SKIPPED_LOCKED";
            }
            doScanScheduledTriggers();
            return "SUCCESS";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[定时触发] 获取集群扫描锁被中断，本轮跳过");
            return "INTERRUPTED";
        } catch (Exception e) {
            log.warn("[定时触发] 获取或执行集群扫描锁失败，本轮跳过: {}", e.getMessage());
            return "FAILED: " + e.getMessage();
        } finally {
            unlockSafely(lock, locked, SCAN_LOCK_KEY);
        }
    }

    private void doScanScheduledTriggers() {
        List<AiBusinessTrigger> triggers = triggerService.selectActiveScheduleTriggers(
                maxTriggersPerRun == null ? DEFAULT_TRIGGER_LIMIT : maxTriggersPerRun);
        if (triggers == null || triggers.isEmpty()) {
            return;
        }
        for (AiBusinessTrigger trigger : triggers) {
            try {
                scanSingleTrigger(trigger);
            } catch (Exception e) {
                log.warn("[定时触发] 扫描触发器失败, triggerId={}, triggerName={}: {}",
                        trigger == null ? null : trigger.getId(),
                        trigger == null ? null : trigger.getTriggerName(),
                        e.getMessage());
            }
        }
    }

    private void scanSingleTrigger(AiBusinessTrigger trigger) {
        if (trigger == null || trigger.getTenantId() == null) {
            return;
        }
        ScheduledTriggerConfig config = readScheduleConfig(trigger);
        if (StringUtils.isBlank(config.dueField())) {
            log.warn("[定时触发] 跳过未配置到期字段的触发器, triggerId={}, triggerName={}",
                    trigger.getId(), trigger.getTriggerName());
            triggerService.touchScheduleScanTime(trigger.getTenantId(), trigger.getId());
            return;
        }
        if (trigger.getLastExecuteTime() != null
                && trigger.getLastExecuteTime().isAfter(LocalDateTime.now().minusMinutes(config.minIntervalMinutes()))) {
            log.debug("[定时触发] 跳过未到最小扫描间隔的触发器, triggerId={}, minIntervalMinutes={}",
                    trigger.getId(), config.minIntervalMinutes());
            return;
        }

        try {
            TenantContextHolder.executeWithTenant(trigger.getTenantId(), () -> scanWithTenant(trigger, config));
        } finally {
            triggerService.touchScheduleScanTime(trigger.getTenantId(), trigger.getId());
        }
    }

    private void scanWithTenant(AiBusinessTrigger trigger, ScheduledTriggerConfig config) {
        String configKey = resolveConfigKey(trigger, config);
        if (StringUtils.isBlank(configKey)) {
            log.warn("[定时触发] 跳过缺少运行配置的触发器, triggerId={}, objectCode={}",
                    trigger.getId(), trigger.getObjectCode());
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(config.lookBehindDays()).atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(config.maxLookAheadDays()).plusDays(1).atStartOfDay().minusNanos(1);
        List<Map<String, Object>> rows = dynamicCrudService.selectScheduledCandidateRows(
                configKey, config.dueField(), windowStart, windowEnd, config.batchSize());
        if (rows == null || rows.isEmpty()) {
            return;
        }

        LocalDateTime dedupeSince = today.atStartOfDay();
        for (Map<String, Object> row : rows) {
            String recordId = readRecordId(configKey, row);
            if (StringUtils.isBlank(recordId)) {
                continue;
            }
            if (triggerService.hasSuccessOrTodoLogSince(trigger.getTenantId(), trigger.getId(), recordId,
                    BusinessEvent.SCHEDULED_DUE, dedupeSince)) {
                continue;
            }
            ReminderTierRule tierRule = matchTierRule(config, row, today);
            if (!config.tierRules().isEmpty() && tierRule == null) {
                continue;
            }
            BusinessEvent event = buildScheduledEvent(trigger, configKey, recordId, row, tierRule);
            if (!triggerExecutor.matchesCondition(trigger, event)) {
                continue;
            }
            executeScheduledEvent(trigger, event, today);
        }
    }

    private void executeScheduledEvent(AiBusinessTrigger trigger, BusinessEvent event, LocalDate today) {
        if (!Boolean.TRUE.equals(clusterLockEnabled)) {
            triggerExecutor.executeTrigger(trigger, event);
            return;
        }
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            triggerExecutor.executeTrigger(trigger, event);
            return;
        }
        String lockKey = buildRecordLockKey(trigger, event, today);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(normalizeLockWaitMs(), TimeUnit.MILLISECONDS);
            if (!locked) {
                log.debug("[定时触发] 到期记录执行锁已被占用, triggerId={}, recordId={}",
                        trigger.getId(), event.getRecordId());
                return;
            }
            triggerExecutor.executeTrigger(trigger, event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[定时触发] 获取到期记录执行锁被中断, triggerId={}, recordId={}",
                    trigger.getId(), event.getRecordId());
        } catch (Exception e) {
            log.warn("[定时触发] 到期记录执行失败, triggerId={}, recordId={}: {}",
                    trigger.getId(), event.getRecordId(), e.getMessage());
        } finally {
            unlockSafely(lock, locked, lockKey);
        }
    }

    private String buildRecordLockKey(AiBusinessTrigger trigger, BusinessEvent event, LocalDate today) {
        return RECORD_LOCK_PREFIX
                + safeLockToken(trigger.getTenantId()) + ":"
                + safeLockToken(trigger.getId()) + ":"
                + safeLockToken(event.getRecordId()) + ":"
                + safeLockToken(today);
    }

    private String safeLockToken(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value).replaceAll("[^A-Za-z0-9:_-]", "_");
    }

    private long normalizeLockWaitMs() {
        if (lockWaitMs == null) {
            return 0L;
        }
        return Math.max(lockWaitMs, 0L);
    }

    private void unlockSafely(RLock lock, boolean locked, String lockKey) {
        if (!locked || lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("[定时触发] 释放分布式锁失败, lockKey={}: {}", lockKey, e.getMessage());
        }
    }

    private void logLocalLockWarning() {
        if (localLockWarningLogged) {
            return;
        }
        localLockWarningLogged = true;
        log.warn("[定时触发] 未检测到 RedissonClient，本轮仅依赖任务调度中心集群调度和日志去重；如需手动并发兜底请启用 Redis/Redisson");
    }

    private ScheduledTriggerConfig readScheduleConfig(AiBusinessTrigger trigger) {
        JSONObject condition = readJson(trigger.getEventCondition());
        JSONObject schedule = condition.getJSONObject("schedule");
        if (schedule == null) {
            schedule = condition;
        }
        JSONObject actionConfig = readJson(trigger.getActionConfig());
        String dueField = StringUtils.firstNonBlank(
                schedule.getString("dueField"),
                schedule.getString("field"),
                schedule.getString("reminderField"),
                actionConfig.getString("dueField")
        );
        String configKey = StringUtils.firstNonBlank(schedule.getString("configKey"), actionConfig.getString("configKey"));
        int batchSize = clamp(readInt(schedule, "batchSize", DEFAULT_BATCH_SIZE), 1, MAX_BATCH_SIZE);
        int minIntervalMinutes = Math.max(readInt(schedule, "minIntervalMinutes", DEFAULT_MIN_INTERVAL_MINUTES),
                DEFAULT_MIN_INTERVAL_MINUTES);
        int lookAheadDays = clamp(readInt(schedule, "lookAheadDays", 0), 0, 365);
        int lookBehindDays = clamp(readInt(schedule, "lookBehindDays", 0), 0, 30);
        List<ReminderTierRule> tierRules = readTierRules(schedule, lookAheadDays);
        return new ScheduledTriggerConfig(dueField, configKey, lookAheadDays, lookBehindDays,
                batchSize, minIntervalMinutes, tierRules);
    }

    private String resolveConfigKey(AiBusinessTrigger trigger, ScheduledTriggerConfig config) {
        if (StringUtils.isNotBlank(config.configKey())) {
            return config.configKey();
        }
        AiBusinessApp app = businessAppMapper.selectRuntimeAppByObject(
                trigger.getTenantId(), trigger.getSuiteCode(), trigger.getObjectCode());
        return app == null ? null : app.getConfigKey();
    }

    private BusinessEvent buildScheduledEvent(AiBusinessTrigger trigger, String configKey,
                                              String recordId, Map<String, Object> row,
                                              ReminderTierRule tierRule) {
        Map<String, Object> eventData = new LinkedHashMap<>(row);
        eventData.put("scheduledTriggerId", trigger.getId());
        eventData.put("scheduledTriggerName", trigger.getTriggerName());
        if (tierRule != null) {
            eventData.put("reminderRuleCode", tierRule.ruleCode());
            eventData.put("reminderRuleName", tierRule.ruleName());
            eventData.put("reminderLookAheadDays", tierRule.lookAheadDays());
            eventData.put("reminderReceiverRule", tierRule.receiverRule());
            eventData.put("reminderMetricField", tierRule.metricField());
            eventData.put("reminderMetricValue", tierRule.metricValue());
        }
        return BusinessEvent.builder()
                .eventType(BusinessEvent.SCHEDULED_DUE)
                .suiteCode(trigger.getSuiteCode())
                .objectCode(trigger.getObjectCode())
                .configKey(configKey)
                .recordId(recordId)
                .recordData(eventData)
                .tenantId(trigger.getTenantId())
                .build();
    }

    private String readRecordId(String configKey, Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        try {
            Object recordId = dynamicCrudService.resolveRecordId(configKey, row);
            if (recordId != null) {
                return String.valueOf(recordId);
            }
        } catch (Exception e) {
            log.debug("[定时触发] 按运行主键解析记录ID失败, configKey={}: {}", configKey, e.getMessage());
        }
        Object value = row.get("id");
        if (value == null) {
            value = row.get("Id");
        }
        return value == null ? null : String.valueOf(value);
    }

    private ReminderTierRule matchTierRule(ScheduledTriggerConfig config, Map<String, Object> row, LocalDate today) {
        if (config.tierRules().isEmpty()) {
            return null;
        }
        LocalDate dueDate = toLocalDate(readRowValue(row, config.dueField()));
        if (dueDate == null) {
            return null;
        }
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        if (daysUntilDue < -config.lookBehindDays()) {
            return null;
        }
        for (ReminderTierRule rule : config.tierRules()) {
            if (daysUntilDue > rule.lookAheadDays()) {
                continue;
            }
            BigDecimal metricValue = StringUtils.isBlank(rule.metricField())
                    ? null
                    : toDecimal(readRowValue(row, rule.metricField()));
            if (StringUtils.isNotBlank(rule.metricField()) && metricValue == null) {
                continue;
            }
            if (metricValue == null && (rule.minValue() != null || rule.maxValue() != null)) {
                continue;
            }
            if (rule.minValue() != null && metricValue.compareTo(rule.minValue()) < 0) {
                continue;
            }
            if (rule.maxValue() != null && metricValue.compareTo(rule.maxValue()) > 0) {
                continue;
            }
            return rule.withMetricValue(metricValue);
        }
        return null;
    }

    private List<ReminderTierRule> readTierRules(JSONObject schedule, int defaultLookAheadDays) {
        JSONArray rules = schedule == null ? null : schedule.getJSONArray("tierRules");
        if (rules == null) {
            rules = schedule == null ? null : schedule.getJSONArray("reminderRules");
        }
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<ReminderTierRule> result = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            String ruleCode = StringUtils.defaultIfBlank(rule.getString("ruleCode"), "rule_" + (i + 1));
            int lookAheadDays = clamp(readInt(rule, "lookAheadDays", defaultLookAheadDays), 0, 365);
            result.add(new ReminderTierRule(
                    ruleCode,
                    StringUtils.defaultIfBlank(rule.getString("ruleName"), ruleCode),
                    StringUtils.trimToNull(rule.getString("metricField")),
                    readDecimal(rule, "minValue", "min", "gte"),
                    readDecimal(rule, "maxValue", "max", "lte"),
                    lookAheadDays,
                    StringUtils.trimToNull(rule.getString("receiverRule")),
                    null
            ));
        }
        return result;
    }

    private Object readRowValue(Map<String, Object> row, String field) {
        if (row == null || StringUtils.isBlank(field)) {
            return null;
        }
        Object value = row.get(field);
        if (value != null) {
            return value;
        }
        String snake = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        value = row.get(snake);
        if (value != null || !field.contains("_")) {
            return value;
        }
        StringBuilder camel = new StringBuilder();
        boolean upperNext = false;
        for (char ch : field.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            camel.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return row.get(camel.toString());
    }

    private BigDecimal readDecimal(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            BigDecimal decimal = toDecimal(value);
            if (decimal != null) {
                return decimal;
            }
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            if (text.length() >= 10) {
                return LocalDate.parse(text.substring(0, 10));
            }
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject readJson(String json) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private int readInt(JSONObject source, String key, int fallback) {
        if (source == null || StringUtils.isBlank(key)) {
            return fallback;
        }
        Integer value = source.getInteger(key);
        return value == null ? fallback : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private record ScheduledTriggerConfig(String dueField,
                                          String configKey,
                                          int lookAheadDays,
                                          int lookBehindDays,
                                          int batchSize,
                                          int minIntervalMinutes,
                                          List<ReminderTierRule> tierRules) {

        private int maxLookAheadDays() {
            return tierRules == null || tierRules.isEmpty()
                    ? lookAheadDays
                    : Math.max(lookAheadDays, tierRules.stream()
                            .mapToInt(ReminderTierRule::lookAheadDays)
                            .max()
                            .orElse(lookAheadDays));
        }
    }

    private record ReminderTierRule(String ruleCode,
                                    String ruleName,
                                    String metricField,
                                    BigDecimal minValue,
                                    BigDecimal maxValue,
                                    int lookAheadDays,
                                    String receiverRule,
                                    BigDecimal metricValue) {

        private ReminderTierRule withMetricValue(BigDecimal value) {
            return new ReminderTierRule(ruleCode, ruleName, metricField, minValue, maxValue,
                    lookAheadDays, receiverRule, value);
        }
    }
}
