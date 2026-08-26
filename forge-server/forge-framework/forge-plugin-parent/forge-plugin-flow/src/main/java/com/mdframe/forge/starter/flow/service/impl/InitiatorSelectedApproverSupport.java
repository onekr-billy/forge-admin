package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.dto.FlowStartConfig;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 发起人自选审批人的 BPMN 与启动变量协议。 */
final class InitiatorSelectedApproverSupport {

    static final String VARIABLE_NAME = "PROCESS_START_USER";
    private static final Pattern COLLECTION_PATTERN = Pattern.compile(
            "^\\$\\{PROCESS_START_USER\\[['\"]([^'\"\\]]+)['\"]\\]\\}$");

    private InitiatorSelectedApproverSupport() {
    }

    static List<FlowStartConfig.ApproverNode> discover(BpmnModel bpmnModel) {
        List<FlowStartConfig.ApproverNode> result = new ArrayList<>();
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            return result;
        }
        for (UserTask task : bpmnModel.getMainProcess().findFlowElementsOfType(UserTask.class)) {
            String nodeKey = resolveNodeKey(task);
            if (nodeKey == null) {
                continue;
            }
            FlowStartConfig.ApproverNode node = new FlowStartConfig.ApproverNode();
            node.setNodeKey(nodeKey);
            node.setNodeName(task.getName());
            node.setMultiple(true);
            result.add(node);
        }
        return result;
    }

    static void validateAndNormalize(BpmnModel bpmnModel, Map<String, Object> variables) {
        List<FlowStartConfig.ApproverNode> nodes = discover(bpmnModel);
        if (nodes.isEmpty()) {
            return;
        }
        Object raw = variables.get(VARIABLE_NAME);
        if (!(raw instanceof Map<?, ?> selections)) {
            throw new IllegalArgumentException("请为发起人自选节点配置审批人");
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (FlowStartConfig.ApproverNode node : nodes) {
            Object value = selections.get(node.getNodeKey());
            Collection<?> users = value instanceof Collection<?> collection ? collection : List.of();
            List<String> ids = users.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(item -> String.valueOf(item).trim())
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                String name = node.getNodeName() == null || node.getNodeName().isBlank()
                        ? node.getNodeKey() : node.getNodeName();
                throw new IllegalArgumentException("请选择节点「" + name + "」的审批人");
            }
            normalized.put(node.getNodeKey(), ids);
        }
        variables.put(VARIABLE_NAME, normalized);
    }

    private static String resolveNodeKey(UserTask task) {
        MultiInstanceLoopCharacteristics loop = task.getLoopCharacteristics();
        if (loop == null) {
            return null;
        }
        String collection = loop.getCollectionString();
        if (collection == null || collection.isBlank()) {
            collection = loop.getInputDataItem();
        }
        Matcher matcher = COLLECTION_PATTERN.matcher(collection == null ? "" : collection.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
