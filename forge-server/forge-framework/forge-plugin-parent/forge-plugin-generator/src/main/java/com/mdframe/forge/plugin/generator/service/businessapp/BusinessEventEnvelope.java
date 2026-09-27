package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 业务事件可信信封。
 * <p>
 * 当前事件只允许由进程内受信生产者创建；来源、协议版本、稳定事件 ID 和载荷摘要
 * 共同用于消费者校验及副作用幂等。若未来开放外部事件入口，应在入口层额外验证 HMAC
 * 或消息中间件签名，再调用本类封装内部事件。
 */
public final class BusinessEventEnvelope {

    public static final int CURRENT_VERSION = 1;
    public static final String SOURCE_DYNAMIC_CRUD = "DYNAMIC_CRUD";
    public static final String SOURCE_FLOW_CALLBACK = "FLOW_CALLBACK";
    public static final String SOURCE_SCHEDULER = "SCHEDULER";

    private static final Set<String> TRUSTED_SOURCES = Set.of(
            SOURCE_DYNAMIC_CRUD, SOURCE_FLOW_CALLBACK, SOURCE_SCHEDULER);
    private static final String EVENT_ID_PREFIX = "BEV1:";

    private BusinessEventEnvelope() {
    }

    public static BusinessEvent stamp(BusinessEvent event, String source) {
        return stamp(event, source, null);
    }

    public static BusinessEvent stamp(BusinessEvent event, String source, String stableSourceKey) {
        if (event == null) {
            return null;
        }
        String normalizedSource = StringUtils.upperCase(StringUtils.trimToEmpty(source));
        if (!TRUSTED_SOURCES.contains(normalizedSource)) {
            throw new IllegalArgumentException("不受信的业务事件来源");
        }
        event.setEventSource(normalizedSource);
        event.setEventVersion(CURRENT_VERSION);
        String eventIdentity = StringUtils.isBlank(stableSourceKey)
                ? UUID.randomUUID().toString()
                : normalizedSource + ":" + event.getTenantId() + ":" + stableSourceKey.trim();
        event.setEventId(EVENT_ID_PREFIX + sha256(eventIdentity));
        event.setEventDigest(payloadDigest(event));
        return event;
    }

    public static boolean isTrusted(BusinessEvent event) {
        if (event == null || event.getTenantId() == null || event.getTenantId() <= 0
                || event.getEventVersion() == null || event.getEventVersion() != CURRENT_VERSION
                || !TRUSTED_SOURCES.contains(event.getEventSource())
                || !isAllowedEventType(event.getEventSource(), event.getEventType())
                || StringUtils.isBlank(event.getEventId()) || event.getEventId().length() > 128
                || StringUtils.isBlank(event.getEventDigest())) {
            return false;
        }
        return MessageDigest.isEqual(
                event.getEventDigest().getBytes(StandardCharsets.US_ASCII),
                payloadDigest(event).getBytes(StandardCharsets.US_ASCII));
    }

    public static String processIdempotencyKey(BusinessEvent event) {
        return "EVENT:" + sha256(event == null ? "" : StringUtils.defaultString(event.getEventId()));
    }

    private static boolean isAllowedEventType(String source, String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return false;
        }
        return switch (source) {
            case SOURCE_DYNAMIC_CRUD -> Set.of(
                    BusinessEvent.RECORD_CREATED,
                    BusinessEvent.RECORD_UPDATED,
                    BusinessEvent.RECORD_DELETED,
                    BusinessEvent.STATUS_CHANGED,
                    BusinessEvent.FIELD_CHANGED,
                    BusinessEvent.FORM_SUBMITTED).contains(eventType);
            case SOURCE_FLOW_CALLBACK -> Set.of(
                    BusinessEvent.FLOW_APPROVED,
                    BusinessEvent.FLOW_REJECTED,
                    BusinessEvent.FLOW_CANCELED).contains(eventType);
            case SOURCE_SCHEDULER -> BusinessEvent.SCHEDULED_DUE.equals(eventType);
            default -> false;
        };
    }

    private static String payloadDigest(BusinessEvent event) {
        return sha256(canonicalJson(commonMaterial(event)));
    }

    private static Map<String, Object> commonMaterial(BusinessEvent event) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("eventId", event.getEventId());
        material.put("eventSource", event.getEventSource());
        material.put("eventVersion", event.getEventVersion());
        material.put("tenantId", event.getTenantId());
        material.put("eventType", event.getEventType());
        material.put("suiteCode", event.getSuiteCode());
        material.put("objectCode", event.getObjectCode());
        material.put("configKey", event.getConfigKey());
        material.put("recordId", event.getRecordId());
        material.put("recordData", event.getRecordData());
        material.put("previousData", event.getPreviousData());
        material.put("operatorId", event.getOperatorId());
        material.put("operatorName", event.getOperatorName());
        return material;
    }

    private static String canonicalJson(Object value) {
        return JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
