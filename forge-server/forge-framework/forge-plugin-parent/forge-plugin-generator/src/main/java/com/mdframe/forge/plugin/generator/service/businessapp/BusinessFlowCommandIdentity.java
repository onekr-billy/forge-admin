package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 为跨服务流程写命令生成稳定、可校验的重试身份。 */
final class BusinessFlowCommandIdentity {

    private BusinessFlowCommandIdentity() {
    }

    static Credentials forTaskAction(String action, Long tenantId, Long userId, String taskId,
                                     String comment, Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comment", StringUtils.defaultString(comment));
        payload.put("variables", variables == null ? Map.of() : variables);
        return create(action, tenantId, userId, taskId, payload);
    }

    static Credentials forProcessAction(String action, Long tenantId, Long userId,
                                        String processInstanceId, String comment) {
        return create(action, tenantId, userId, processInstanceId,
                Map.of("comment", StringUtils.defaultString(comment)));
    }

    private static Credentials create(String action, Long tenantId, Long userId,
                                      String resourceId, Map<String, Object> payload) {
        if (tenantId == null || tenantId <= 0 || userId == null
                || StringUtils.isAnyBlank(action, resourceId)) {
            throw new BusinessException("流程命令缺少可信身份");
        }
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("action", action.trim().toUpperCase(Locale.ROOT));
        material.put("tenantId", tenantId);
        material.put("userId", userId);
        material.put("resourceId", resourceId.trim());
        material.put("payload", payload == null ? Map.of() : payload);
        String digest = sha256(JSON.toJSONString(material, JSONWriter.Feature.SortMapEntriesByKeys));
        return new Credentials("flow:" + digest, digest);
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

    record Credentials(String idempotencyKey, String requestDigest) {
    }
}
