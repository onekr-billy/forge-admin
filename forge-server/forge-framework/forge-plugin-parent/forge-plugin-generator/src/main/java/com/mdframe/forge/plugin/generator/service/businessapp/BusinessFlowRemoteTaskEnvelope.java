package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

/** 远程任务命令的稳定身份和不可变请求摘要。 */
final class BusinessFlowRemoteTaskEnvelope {

    static final String COMMAND_RESUBMIT = "RESUBMIT";
    private static final Set<String> SUPPORTED_COMMANDS = Set.of(COMMAND_RESUBMIT);

    private BusinessFlowRemoteTaskEnvelope() {
    }

    static String commandKey(BusinessFlowRemoteTaskRequest request) {
        return commandKey(request.getTenantId(), request.getCommandType(), request.getIdempotencyKey());
    }

    static String commandKey(Long tenantId, String commandType, String idempotencyKey) {
        return sha256(tenantId + ":" + StringUtils.trimToEmpty(commandType).toUpperCase(Locale.ROOT)
                + ":" + StringUtils.trimToEmpty(idempotencyKey));
    }

    static String requestPayload(BusinessFlowRemoteTaskRequest request) {
        return JSON.toJSONString(request, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    static String requestDigest(BusinessFlowRemoteTaskRequest request) {
        return sha256(requestPayload(request));
    }

    static BusinessFlowRemoteTaskRequest restore(String payload) {
        return JSON.parseObject(payload, BusinessFlowRemoteTaskRequest.class);
    }

    static boolean supports(String commandType) {
        return SUPPORTED_COMMANDS.contains(StringUtils.trimToEmpty(commandType).toUpperCase(Locale.ROOT));
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
