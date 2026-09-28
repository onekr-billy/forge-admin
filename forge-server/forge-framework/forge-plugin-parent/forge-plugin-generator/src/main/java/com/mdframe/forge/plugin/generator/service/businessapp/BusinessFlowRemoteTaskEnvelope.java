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

    static final String COMMAND_APPROVE = "APPROVE";
    static final String COMMAND_REJECT = "REJECT";
    static final String COMMAND_REJECT_TO_START = "REJECT_TO_START";
    static final String COMMAND_RETURN = "RETURN";
    static final String COMMAND_RESUBMIT = "RESUBMIT";
    private static final Set<String> SUPPORTED_COMMANDS = Set.of(
            COMMAND_APPROVE, COMMAND_REJECT, COMMAND_REJECT_TO_START, COMMAND_RETURN, COMMAND_RESUBMIT);

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

    static String commandTypeForAction(String action) {
        return switch (StringUtils.trimToEmpty(action).toLowerCase(Locale.ROOT)) {
            case "approve" -> COMMAND_APPROVE;
            case "reject" -> COMMAND_REJECT;
            case "rejecttostart" -> COMMAND_REJECT_TO_START;
            case "return" -> COMMAND_RETURN;
            default -> throw new IllegalArgumentException("不支持的流程任务动作");
        };
    }

    static String actionForCommandType(String commandType) {
        return switch (StringUtils.trimToEmpty(commandType).toUpperCase(Locale.ROOT)) {
            case COMMAND_APPROVE -> "approve";
            case COMMAND_REJECT -> "reject";
            case COMMAND_REJECT_TO_START -> "rejecttostart";
            case COMMAND_RETURN -> "return";
            default -> throw new IllegalArgumentException("不是可办理的流程任务命令");
        };
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
