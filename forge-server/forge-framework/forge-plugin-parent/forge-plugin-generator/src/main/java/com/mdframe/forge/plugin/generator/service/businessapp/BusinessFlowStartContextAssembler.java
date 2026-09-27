package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.camelToSnake;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.read;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.snakeToCamel;

/**
 * 流程启动上下文组装器。
 * <p>
 * 通过 Assembler 集中变量映射和标题渲染，并以策略对象隔离调用方变量校验。
 * 该组件不访问数据库或 Flowable，也不拥有事务边界。
 */
final class BusinessFlowStartContextAssembler {

    private static final Set<String> SERVER_OWNED_VARIABLES = Set.of(
            "objectCode", "configKey", "recordId", "businessKey",
            "documentBusinessKey", "recordBusinessKey", "flowBusinessKey");

    private final RequestedVariablePolicy requestedVariablePolicy;

    BusinessFlowStartContextAssembler(RequestedVariablePolicy requestedVariablePolicy) {
        this.requestedVariablePolicy = requestedVariablePolicy;
    }

    static BusinessFlowStartContextAssembler standard() {
        return new BusinessFlowStartContextAssembler(
                new ReservedVariablePolicy(SERVER_OWNED_VARIABLES));
    }

    StartContext assemble(JSONObject bindingConfig,
                          Map<String, Object> recordData,
                          Map<String, Object> requestedVariables,
                          String objectCode) {
        Map<String, Object> variables = buildVariables(bindingConfig, recordData);
        mergeRequestedVariables(variables, requestedVariables);
        return new StartContext(variables, buildTitle(bindingConfig, recordData, objectCode));
    }

    Map<String, Object> buildVariables(JSONObject bindingConfig, Map<String, Object> recordData) {
        Map<String, Object> variables = new HashMap<>();
        JSONArray variableMapping = bindingConfig.getJSONArray("variableMapping");

        if (recordData != null) {
            for (Map.Entry<String, Object> entry : recordData.entrySet()) {
                putBusinessFieldVariable(variables, entry.getKey(), entry.getValue());
            }
        }

        if (variableMapping != null && recordData != null) {
            for (int i = 0; i < variableMapping.size(); i++) {
                JSONObject mapping = variableMapping.getJSONObject(i);
                String formField = StringUtils.defaultIfBlank(
                        mapping.getString("formField"), mapping.getString("field"));
                String flowVariable = StringUtils.defaultIfBlank(
                        mapping.getString("flowVariable"), mapping.getString("variable"));
                Object value = read(recordData, formField);
                if (value != null && StringUtils.isNotBlank(flowVariable)) {
                    variables.put(flowVariable, value);
                }
            }
        }
        return variables;
    }

    void mergeRequestedVariables(Map<String, Object> target, Map<String, Object> requestedVariables) {
        if (requestedVariables == null || requestedVariables.isEmpty()) {
            return;
        }
        requestedVariablePolicy.validate(requestedVariables);
        target.putAll(requestedVariables);
    }

    String buildTitle(JSONObject bindingConfig, Map<String, Object> recordData, String objectCode) {
        return renderTitle(bindingConfig.getString("titleTemplate"), recordData, objectCode, null, null);
    }

    String renderTitle(String titleTemplate,
                       Map<String, Object> recordData,
                       String objectCode,
                       String starterName,
                       String objectName) {
        String fallback = StringUtils.defaultIfBlank(objectCode, "业务") + " 审批申请";
        if (StringUtils.isBlank(titleTemplate)) {
            return fallback;
        }
        Map<String, String> extras = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(objectCode)) {
            extras.put("objectCode", objectCode);
        }
        if (StringUtils.isNotBlank(objectName)) {
            extras.put("objectName", objectName);
        }
        if (StringUtils.isNotBlank(starterName)) {
            extras.put("starterName", starterName);
            extras.put("initiatorName", starterName);
            extras.put("initiator", starterName);
        }
        return BusinessApprovalTitleRenderer.render(titleTemplate, recordData, extras, fallback);
    }

    private void putBusinessFieldVariable(Map<String, Object> variables, String field, Object value) {
        String key = StringUtils.trimToNull(field);
        if (key == null || value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return;
        }
        variables.putIfAbsent(key, value);
        variables.putIfAbsent(snakeToCamel(key), value);
        variables.putIfAbsent(camelToSnake(key), value);
    }

    record StartContext(Map<String, Object> variables, String title) {
    }

    @FunctionalInterface
    interface RequestedVariablePolicy {
        void validate(Map<String, Object> requestedVariables);
    }

    private record ReservedVariablePolicy(Set<String> reservedVariables) implements RequestedVariablePolicy {

        @Override
        public void validate(Map<String, Object> requestedVariables) {
            List<String> reserved = requestedVariables.keySet().stream()
                    .filter(reservedVariables::contains)
                    .sorted()
                    .toList();
            if (!reserved.isEmpty()) {
                throw new BusinessException("启动变量不能覆盖服务端业务上下文：" + String.join(", ", reserved));
            }
        }
    }
}
