package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Flow 回调事件的可靠身份、聚合键和不可变摘要。 */
final class BusinessFlowCallbackInboxEnvelope {

    private BusinessFlowCallbackInboxEnvelope() {
    }

    static boolean hasReliableIdentity(FlowEventContext event) {
        return event != null && event.getTenantId() != null && event.getTenantId() > 0
                && StringUtils.isNotBlank(event.getEventId())
                && event.getEventVersion() != null && event.getEventVersion() > 0
                && event.getEventSequence() != null && event.getEventSequence() > 0
                && StringUtils.isNotBlank(event.getEvent())
                && (StringUtils.isNotBlank(event.getProcessInstanceId())
                    || StringUtils.isNotBlank(event.getBusinessKey()));
    }

    static String payload(FlowEventContext event) {
        return JSON.toJSONString(event, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    static String digest(FlowEventContext event) {
        return sha256(payload(event));
    }

    static String aggregateKey(FlowEventContext event) {
        String identity = StringUtils.firstNonBlank(
                StringUtils.trimToNull(event.getProcessInstanceId()),
                StringUtils.trimToNull(event.getBusinessKey()));
        return sha256(event.getTenantId() + ":FLOW_CALLBACK:" + identity);
    }

    static FlowEventContext restore(String payload) {
        return JSON.parseObject(payload, FlowEventContext.class);
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
