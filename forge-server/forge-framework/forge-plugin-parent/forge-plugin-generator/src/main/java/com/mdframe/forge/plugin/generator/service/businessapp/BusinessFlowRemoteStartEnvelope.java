package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 远程启动命令的稳定身份和不可变请求摘要。 */
final class BusinessFlowRemoteStartEnvelope {

    static final String COMMAND_TYPE = "START";

    private BusinessFlowRemoteStartEnvelope() {
    }

    static String commandKey(BusinessFlowRemoteStartRequest request) {
        return sha256(request.getTenantId() + ":" + COMMAND_TYPE + ":" + request.getFlowBusinessKey());
    }

    static String requestPayload(BusinessFlowRemoteStartRequest request) {
        return JSON.toJSONString(request, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    static String requestDigest(BusinessFlowRemoteStartRequest request) {
        return sha256(requestPayload(request));
    }

    static BusinessFlowRemoteStartRequest restore(String payload) {
        return JSON.parseObject(payload, BusinessFlowRemoteStartRequest.class);
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
