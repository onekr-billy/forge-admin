package com.mdframe.forge.starter.flow.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.flow.dto.FlowApprovalPointDTO;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 BPMN 节点上的审批职责、审批要点等扩展策略。
 */
@Slf4j
public final class FlowNodePolicyParser {

    public static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FlowNodePolicyParser() {
    }

    public static String resolveResponsibilityDescription(FlowNode flowNode) {
        return trimToNull(readStringAttribute(flowNode, "responsibilityDescription"));
    }

    public static List<FlowApprovalPointDTO> resolveApprovalPoints(FlowNode flowNode) {
        String json = readStringAttribute(flowNode, "approvalPoints");
        List<FlowApprovalPointDTO> configured = parseList(
                json, new TypeReference<List<FlowApprovalPointDTO>>() {}, "approvalPoints");
        List<FlowApprovalPointDTO> normalized = normalizeApprovalPoints(configured);
        if (!normalized.isEmpty()) {
            return normalized;
        }

        String legacy = trimToNull(readStringAttribute(flowNode, "responsibility"));
        if (legacy == null) {
            return Collections.emptyList();
        }
        FlowApprovalPointDTO point = new FlowApprovalPointDTO();
        point.setId("legacy-responsibility");
        point.setContent(legacy);
        point.setRequired(false);
        point.setSort(1);
        return List.of(point);
    }

    public static String readStringAttribute(FlowNode flowNode, String name) {
        if (flowNode == null) {
            return null;
        }
        String value = flowNode.getAttributeValue(FLOWABLE_NS, name);
        if (trimToNull(value) != null) {
            return value;
        }
        Map<String, List<ExtensionElement>> extensions = flowNode.getExtensionElements();
        List<ExtensionElement> elements = extensions != null ? extensions.get(name) : null;
        return elements != null && !elements.isEmpty() ? elements.get(0).getElementText() : null;
    }

    private static List<FlowApprovalPointDTO> normalizeApprovalPoints(List<FlowApprovalPointDTO> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, FlowApprovalPointDTO> unique = new LinkedHashMap<>();
        int index = 0;
        for (FlowApprovalPointDTO item : source) {
            String content = item == null ? null : trimToNull(item.getContent());
            if (content == null) {
                continue;
            }
            index++;
            String id = trimToNull(item.getId());
            if (id == null) {
                id = "point-" + index + "-" + Integer.toUnsignedString(content.hashCode(), 36);
            }
            FlowApprovalPointDTO normalized = new FlowApprovalPointDTO();
            normalized.setId(id);
            normalized.setContent(content);
            normalized.setRequired(Boolean.TRUE.equals(item.getRequired()));
            normalized.setSort(item.getSort() == null ? index : item.getSort());
            unique.putIfAbsent(id, normalized);
        }
        return unique.values().stream()
                .sorted(java.util.Comparator.comparing(FlowApprovalPointDTO::getSort)
                        .thenComparing(FlowApprovalPointDTO::getId))
                .toList();
    }

    private static <T> List<T> parseList(String json, TypeReference<List<T>> type, String attribute) {
        if (trimToNull(json) == null) {
            return Collections.emptyList();
        }
        try {
            List<T> value = OBJECT_MAPPER.readValue(json, type);
            return value == null ? Collections.emptyList() : value;
        } catch (Exception e) {
            log.warn("忽略无法解析的流程节点策略: attribute={}", attribute);
            return Collections.emptyList();
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
