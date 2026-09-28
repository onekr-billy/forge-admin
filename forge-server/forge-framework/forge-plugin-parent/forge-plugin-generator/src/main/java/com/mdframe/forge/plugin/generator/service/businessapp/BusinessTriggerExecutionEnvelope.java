package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * 可恢复业务触发器命令信封。
 *
 * <p>执行日志保存事件和触发器配置的不可变快照。恢复时只重放快照，不读取可能已经被
 * 修改的当前触发器配置；摘要用于识别数据库传输或人工处理导致的快照不一致。</p>
 */
public final class BusinessTriggerExecutionEnvelope {

    private static final Set<String> REPLAY_SAFE_ACTIONS = Set.of(
            "START_FLOW", "SEND_MESSAGE", "UPDATE_FIELD", "BUSINESS_ACTION", "ACTION", "COMMAND");

    private BusinessTriggerExecutionEnvelope() {
    }

    public static void snapshot(AiBusinessTriggerLog log, AiBusinessTrigger trigger, BusinessEvent event) {
        if (log == null || trigger == null || event == null) {
            throw new BusinessException("业务触发器执行快照不能为空");
        }
        String eventPayload = canonicalJson(JSON.parseObject(JSON.toJSONString(event)));
        String triggerPayload = canonicalJson(JSON.parseObject(JSON.toJSONString(trigger)));
        log.setEventData(eventPayload);
        log.setTriggerSnapshot(triggerPayload);
        log.setExecutionDigest(executionDigest(eventPayload, triggerPayload));
    }

    public static ExecutionCommand restore(AiBusinessTriggerLog log) {
        if (log == null || StringUtils.isBlank(log.getEventData())
                || StringUtils.isBlank(log.getTriggerSnapshot())
                || StringUtils.isBlank(log.getExecutionDigest())) {
            throw new BusinessException("业务触发器执行快照不完整");
        }
        JSONObject eventJson = parseObject(log.getEventData(), "业务事件快照");
        JSONObject triggerJson = parseObject(log.getTriggerSnapshot(), "触发器配置快照");
        String actualDigest = executionDigest(canonicalJson(eventJson), canonicalJson(triggerJson));
        if (!MessageDigest.isEqual(actualDigest.getBytes(StandardCharsets.US_ASCII),
                log.getExecutionDigest().getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException("业务触发器执行快照摘要不一致");
        }
        BusinessEvent event = eventJson.toJavaObject(BusinessEvent.class);
        AiBusinessTrigger trigger = triggerJson.toJavaObject(AiBusinessTrigger.class);
        validateIdentity(log, trigger, event);
        return new ExecutionCommand(trigger, event);
    }

    public static boolean isReplaySafe(AiBusinessTrigger trigger) {
        if (trigger == null) {
            return false;
        }
        String actionType = StringUtils.upperCase(StringUtils.trimToEmpty(trigger.getActionType()));
        if (REPLAY_SAFE_ACTIONS.contains(actionType)) {
            return true;
        }
        JSONObject config = parseObjectOrEmpty(trigger.getActionConfig());
        return StringUtils.isNotBlank(StringUtils.firstNonBlank(
                config.getString("actionCode"),
                config.getString("businessActionCode"),
                config.getString("commandActionCode")));
    }

    public static String idempotencyKey(AiBusinessTrigger trigger, BusinessEvent event, String scope) {
        String raw = "trigger:"
                + StringUtils.defaultString(trigger == null || trigger.getId() == null
                ? null : String.valueOf(trigger.getId()))
                + ":" + StringUtils.defaultString(event == null ? null : event.getEventId())
                + ":" + StringUtils.defaultIfBlank(scope, "action").trim();
        return raw.length() <= 128 ? raw : StringUtils.left(raw, 88) + ":" + sha256(raw).substring(0, 32);
    }

    private static void validateIdentity(AiBusinessTriggerLog log, AiBusinessTrigger trigger, BusinessEvent event) {
        boolean valid = trigger != null && event != null
                && BusinessEventEnvelope.isTrusted(event)
                && log.getTenantId() != null && log.getTenantId().equals(event.getTenantId())
                && log.getTenantId().equals(trigger.getTenantId())
                && log.getTriggerId() != null && log.getTriggerId().equals(trigger.getId())
                && StringUtils.equals(log.getEventId(), event.getEventId())
                && StringUtils.equals(log.getEventDigest(), event.getEventDigest())
                && StringUtils.equals(log.getEventSource(), event.getEventSource())
                && java.util.Objects.equals(log.getEventVersion(), event.getEventVersion())
                && StringUtils.equals(log.getEventType(), event.getEventType())
                && StringUtils.equals(log.getObjectCode(), event.getObjectCode())
                && StringUtils.equals(log.getActionType(), trigger.getActionType());
        if (!valid) {
            throw new BusinessException("业务触发器执行快照身份不一致");
        }
    }

    private static JSONObject parseObject(String value, String label) {
        try {
            JSONObject result = JSON.parseObject(value);
            if (result == null) {
                throw new IllegalArgumentException("empty json");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new BusinessException(label + "不是有效JSON");
        }
    }

    private static JSONObject parseObjectOrEmpty(String value) {
        if (StringUtils.isBlank(value)) {
            return new JSONObject();
        }
        try {
            JSONObject result = JSON.parseObject(value);
            return result == null ? new JSONObject() : result;
        } catch (RuntimeException exception) {
            return new JSONObject();
        }
    }

    private static String executionDigest(String eventPayload, String triggerPayload) {
        return sha256(canonicalJson(JSON.parseObject(eventPayload)) + "\n"
                + canonicalJson(JSON.parseObject(triggerPayload)));
    }

    private static String canonicalJson(Object value) {
        return JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    public record ExecutionCommand(AiBusinessTrigger trigger, BusinessEvent event) {
    }
}
