package com.mdframe.forge.plugin.generator.businessprocess.validation;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessEdge;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessValidationVO;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the structural protocol of a business-process graph.
 *
 * <p>Node-specific business rules remain pluggable through
 * {@link NodeConfigValidator}; this strategy owns only graph identity, ports,
 * edges and topology.</p>
 */
@Component
public class BusinessProcessGraphValidator {

    private static final Pattern GRAPH_ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,127}");
    private static final Set<String> NODE_TYPES = Set.of(
            "START_MANUAL", "START_EVENT", "START_SCHEDULE", "CONDITION",
            "ACTION", "APPROVAL", "SUB_PROCESS", "END");
    private static final Set<String> START_TYPES = Set.of(
            "START_MANUAL", "START_EVENT", "START_SCHEDULE");
    private static final Set<String> APPROVAL_PORTS = Set.of(
            "APPROVED", "REJECTED", "CANCELED", "FAILED");

    public void validate(BusinessProcessSchema schema,
                         BusinessProcessValidationVO result,
                         NodeConfigValidator nodeConfigValidator) {
        Map<String, BusinessProcessNode> nodesById = new LinkedHashMap<>();
        Map<String, String> nodePaths = new HashMap<>();
        List<BusinessProcessNode> startNodes = new ArrayList<>();
        List<BusinessProcessNode> endNodes = new ArrayList<>();

        for (int index = 0; index < schema.getNodes().size(); index++) {
            BusinessProcessNode node = schema.getNodes().get(index);
            String path = "nodes[" + index + "]";
            if (node == null) {
                error(result, "NODE_REQUIRED", "节点不能为空", null, path, "删除空节点后重试");
                continue;
            }
            if (node.getId() == null || !GRAPH_ID_PATTERN.matcher(node.getId()).matches()) {
                error(result, "NODE_ID_INVALID", "节点 ID 格式无效", node.getId(), path + ".id",
                        "使用字母开头的稳定节点 ID");
                continue;
            }
            if (nodesById.putIfAbsent(node.getId(), node) != null) {
                error(result, "NODE_ID_DUPLICATE", "节点 ID 重复", node.getId(), path + ".id",
                        "为复制节点生成新的稳定 ID");
                continue;
            }
            nodePaths.put(node.getId(), path);
            if (!NODE_TYPES.contains(node.getType())) {
                error(result, "NODE_TYPE_UNKNOWN", "节点类型不在首版注册表中", node.getId(),
                        path + ".type", "从节点面板重新选择受支持类型");
            }
            if (node.getName() == null || node.getName().isBlank()) {
                error(result, "NODE_NAME_REQUIRED", "节点名称不能为空", node.getId(),
                        path + ".name", "填写便于运行时间线识别的节点名称");
            }
            validateDeclaredPorts(node, path, result);
            if (START_TYPES.contains(node.getType())) {
                startNodes.add(node);
            }
            if ("END".equals(node.getType())) {
                endNodes.add(node);
            }
            nodeConfigValidator.validate(node, path);
        }

        if (startNodes.size() != 1) {
            error(result, "START_NODE_COUNT", "每个业务流程必须且只能有一个开始节点", null,
                    "nodes", "保留一个开始节点，其他触发方式拆分为独立流程");
        }
        if (endNodes.isEmpty()) {
            error(result, "END_NODE_REQUIRED", "业务流程至少需要一个结束节点", null,
                    "nodes", "为每个结果分支连接明确结束节点");
        }
        validateStartRecordSource(schema, startNodes, result);
        List<BusinessProcessEdge> graphEdges = validateEdges(
                schema.getEdges(), nodesById, nodePaths, result);
        validateTopology(nodesById, startNodes, endNodes, graphEdges, nodePaths, result);
    }

    private void validateStartRecordSource(BusinessProcessSchema schema,
                                           List<BusinessProcessNode> starts,
                                           BusinessProcessValidationVO result) {
        if (starts.size() != 1 || schema.getSubject() == null) {
            return;
        }
        String expected = switch (starts.get(0).getType()) {
            case "START_MANUAL" -> "RUNTIME_RECORD";
            case "START_EVENT" -> "EVENT_RECORD";
            case "START_SCHEDULE" -> "SCHEDULE_SCAN_RECORD";
            default -> null;
        };
        if (expected != null && !expected.equals(schema.getSubject().getRecordIdSource())) {
            error(result, "RECORD_ID_SOURCE_MISMATCH", "记录来源与开始节点类型不匹配", starts.get(0).getId(),
                    "subject.recordIdSource", "使用开始节点对应的受控记录来源");
        }
    }

    private void validateDeclaredPorts(BusinessProcessNode node,
                                       String path,
                                       BusinessProcessValidationVO result) {
        Set<String> ports = new LinkedHashSet<>(node.getPorts());
        if (Set.of("START_MANUAL", "START_EVENT", "START_SCHEDULE", "ACTION", "SUB_PROCESS")
                .contains(node.getType()) && !ports.isEmpty() && !ports.equals(Set.of("NEXT"))) {
            error(result, "NODE_PORTS_INVALID", "节点声明了注册表之外的出口", node.getId(),
                    path + ".ports", "移除自定义出口并使用 NEXT");
        }
        if ("END".equals(node.getType()) && !ports.isEmpty()) {
            error(result, "NODE_PORTS_INVALID", "结束节点不能声明出口", node.getId(),
                    path + ".ports", "移除结束节点出口");
        }
    }

    private List<BusinessProcessEdge> validateEdges(List<BusinessProcessEdge> edges,
                                                    Map<String, BusinessProcessNode> nodesById,
                                                    Map<String, String> nodePaths,
                                                    BusinessProcessValidationVO result) {
        Set<String> edgeIds = new HashSet<>();
        Set<String> sourcePorts = new HashSet<>();
        Map<String, Integer> defaultCounts = new HashMap<>();
        List<BusinessProcessEdge> graphEdges = new ArrayList<>();

        for (int index = 0; index < edges.size(); index++) {
            BusinessProcessEdge edge = edges.get(index);
            String path = "edges[" + index + "]";
            if (edge == null) {
                error(result, "EDGE_REQUIRED", "连线不能为空", null, path, "删除空连线后重试");
                continue;
            }
            if (edge.getId() == null || !GRAPH_ID_PATTERN.matcher(edge.getId()).matches()) {
                error(result, "EDGE_ID_INVALID", "连线 ID 格式无效", null, path + ".id",
                        "重新连接节点以生成稳定 ID");
            } else if (!edgeIds.add(edge.getId())) {
                error(result, "EDGE_ID_DUPLICATE", "连线 ID 重复", null, path + ".id",
                        "为重复连线生成新 ID");
            }

            BusinessProcessNode source = nodesById.get(edge.getSource());
            BusinessProcessNode target = nodesById.get(edge.getTarget());
            if (source == null) {
                error(result, "EDGE_SOURCE_MISSING", "连线来源节点不存在", null, path + ".source",
                        "删除悬空连线或恢复来源节点");
            }
            if (target == null) {
                error(result, "EDGE_TARGET_MISSING", "连线目标节点不存在", source == null ? null : source.getId(),
                        path + ".target", "删除悬空连线或恢复目标节点");
            }
            if (Objects.equals(edge.getSource(), edge.getTarget()) && source != null) {
                error(result, "EDGE_SELF_LOOP", "节点不能连接到自身", source.getId(), path,
                        "删除自环并使用明确后继节点");
            }
            if (source != null) {
                Set<String> allowedPorts = allowedPorts(source);
                if (edge.getSourcePort() == null || !allowedPorts.contains(edge.getSourcePort())) {
                    error(result, "EDGE_PORT_INVALID", "连线出口不属于来源节点注册表", source.getId(),
                            path + ".sourcePort", "从来源节点的有效出口重新连接");
                }
                String sourcePortKey = source.getId() + "\u0000" + edge.getSourcePort();
                if (!sourcePorts.add(sourcePortKey)) {
                    error(result, "EDGE_PORT_DUPLICATE", "同一节点出口只能连接一个后继节点", source.getId(),
                            path + ".sourcePort", "合并重复连线或增加明确条件分支");
                }
                if (Boolean.TRUE.equals(edge.getIsDefault())) {
                    if (!"CONDITION".equals(source.getType())) {
                        error(result, "DEFAULT_EDGE_TYPE_INVALID", "只有条件节点允许默认出口", source.getId(),
                                path + ".isDefault", "移除默认标记");
                    } else if (!conditionDefaultPorts(source).contains(edge.getSourcePort())) {
                        error(result, "DEFAULT_EDGE_MISMATCH", "默认连线与条件默认分支不一致", source.getId(),
                                path + ".sourcePort", "同步分支和连线的默认出口");
                    }
                    defaultCounts.merge(source.getId(), 1, Integer::sum);
                }
            }
            if (source != null && target != null) {
                graphEdges.add(edge);
            }
        }
        defaultCounts.forEach((nodeId, count) -> {
            if (count > 1) {
                error(result, "DEFAULT_EDGE_DUPLICATE", "条件节点最多允许一条默认连线", nodeId,
                        nodePaths.get(nodeId) + ".ports", "只保留一条默认出口连线");
            }
        });
        return graphEdges;
    }

    private Set<String> conditionDefaultPorts(BusinessProcessNode node) {
        Set<String> defaults = new LinkedHashSet<>();
        for (Object item : list(node.getConfig().get("branches"))) {
            Map<String, Object> branch = map(item);
            if (Boolean.TRUE.equals(booleanValue(branch.get("isDefault")))) {
                String port = upper(string(branch.get("port")));
                if (port != null) {
                    defaults.add(port);
                }
            }
        }
        return defaults;
    }

    private Set<String> allowedPorts(BusinessProcessNode node) {
        return switch (node.getType()) {
            case "START_MANUAL", "START_EVENT", "START_SCHEDULE", "ACTION", "SUB_PROCESS" -> Set.of("NEXT");
            case "APPROVAL" -> APPROVAL_PORTS;
            case "CONDITION" -> new LinkedHashSet<>(node.getPorts());
            case "END" -> Set.of();
            default -> Set.of();
        };
    }

    private void validateTopology(Map<String, BusinessProcessNode> nodesById,
                                  List<BusinessProcessNode> starts,
                                  List<BusinessProcessNode> ends,
                                  List<BusinessProcessEdge> edges,
                                  Map<String, String> nodePaths,
                                  BusinessProcessValidationVO result) {
        Map<String, List<String>> outgoing = adjacency(nodesById.keySet());
        Map<String, List<String>> incoming = adjacency(nodesById.keySet());
        for (BusinessProcessEdge edge : edges) {
            outgoing.get(edge.getSource()).add(edge.getTarget());
            incoming.get(edge.getTarget()).add(edge.getSource());
        }
        for (BusinessProcessNode node : nodesById.values()) {
            String path = nodePaths.get(node.getId());
            if (START_TYPES.contains(node.getType()) && !incoming.get(node.getId()).isEmpty()) {
                error(result, "START_NODE_HAS_INCOMING", "开始节点不能有入边", node.getId(), path,
                        "删除指向开始节点的连线");
            }
            if ("END".equals(node.getType()) && !outgoing.get(node.getId()).isEmpty()) {
                error(result, "END_NODE_HAS_OUTGOING", "结束节点不能有出边", node.getId(), path,
                        "删除结束节点之后的连线");
            }
            if (!"END".equals(node.getType()) && outgoing.get(node.getId()).isEmpty()) {
                error(result, "NODE_SUCCESSOR_REQUIRED", "非结束节点必须连接后继节点", node.getId(), path,
                        "连接到动作、审批、条件或结束节点");
            }
            if (!START_TYPES.contains(node.getType()) && incoming.get(node.getId()).isEmpty()) {
                error(result, "NODE_PREDECESSOR_REQUIRED", "非开始节点必须有入边", node.getId(), path,
                        "从可达前驱节点连接该节点");
            }
        }
        if (containsCycle(nodesById.keySet(), outgoing, incoming)) {
            error(result, "GRAPH_CYCLE", "业务画布必须是有向无环图", null, "edges",
                    "删除循环连线；审批内部循环请在 Flowable 模型中表达");
        }
        if (starts.size() == 1) {
            Set<String> reachable = traverse(Set.of(starts.get(0).getId()), outgoing);
            for (String nodeId : nodesById.keySet()) {
                if (!reachable.contains(nodeId)) {
                    error(result, "NODE_UNREACHABLE", "节点无法从开始节点到达", nodeId,
                            nodePaths.get(nodeId), "连接到开始节点所在主图或删除孤立节点");
                }
            }
        }
        if (!ends.isEmpty()) {
            Set<String> endIds = new LinkedHashSet<>();
            ends.forEach(node -> endIds.add(node.getId()));
            Set<String> reachesEnd = traverse(endIds, incoming);
            for (String nodeId : nodesById.keySet()) {
                if (!reachesEnd.contains(nodeId)) {
                    error(result, "END_PATH_MISSING", "节点不存在通往结束节点的路径", nodeId,
                            nodePaths.get(nodeId), "为该分支连接明确结束结果");
                }
            }
        }
    }

    private boolean containsCycle(Set<String> nodeIds,
                                  Map<String, List<String>> outgoing,
                                  Map<String, List<String>> incoming) {
        Map<String, Integer> indegrees = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String nodeId : nodeIds) {
            int degree = incoming.get(nodeId).size();
            indegrees.put(nodeId, degree);
            if (degree == 0) {
                queue.add(nodeId);
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (String target : outgoing.get(current)) {
                int degree = indegrees.computeIfPresent(target, (key, value) -> value - 1);
                if (degree == 0) {
                    queue.addLast(target);
                }
            }
        }
        return visited != nodeIds.size();
    }

    private Set<String> traverse(Set<String> roots, Map<String, List<String>> adjacency) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        return visited;
    }

    private Map<String, List<String>> adjacency(Set<String> nodeIds) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        nodeIds.forEach(nodeId -> result.put(nodeId, new ArrayList<>()));
        return result;
    }

    private List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(java.util.Locale.ROOT);
    }

    private void error(BusinessProcessValidationVO result,
                       String code,
                       String message,
                       String nodeId,
                       String path,
                       String suggestion) {
        result.addError(code, message, nodeId, path, suggestion);
    }

    @FunctionalInterface
    public interface NodeConfigValidator {
        void validate(BusinessProcessNode node, String path);
    }
}
