package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.helper.BpmnXmlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 模型部署前的 BPMN 文本兼容处理与可执行性预检。 */
@Slf4j
final class FlowModelBpmnPreflight {

    private FlowModelBpmnPreflight() {
    }

    static String extractProcessKey(String bpmnXml) {
        BpmnXmlUtils.BpmnAnalysis analysis = analyzeSingleProcess(bpmnXml);
        if (!hasText(analysis.processId())) {
            throw new RuntimeException("BPMN process 缺少 id 属性");
        }
        return analysis.processId();
    }

    static String replaceProcessId(String bpmnXml, String modelKey) {
        String currentProcessId = extractProcessKey(bpmnXml);
        if (currentProcessId.equals(modelKey)) {
            return bpmnXml;
        }
        log.info("将流程ID从 {} 替换为 {}", currentProcessId, modelKey);
        return BpmnXmlUtils.replaceSingleProcessId(bpmnXml, modelKey);
    }

    static void validateSequenceFlowRefs(String bpmnXml) {
        BpmnXmlUtils.BpmnAnalysis analysis = analyzeSingleProcess(bpmnXml);
        for (BpmnXmlUtils.BpmnSequenceFlowInfo flow : analysis.sequenceFlows()) {
            if (!hasText(flow.targetRef()) || !hasText(flow.sourceRef())) {
                String missing = !hasText(flow.targetRef()) ? "targetRef" : "sourceRef";
                String flowId = hasText(flow.id()) ? flow.id() : "unknown";
                log.error("BPMN sequenceFlow [{}] 缺少 {} 属性", flowId, missing);
                throw new RuntimeException(String.format(
                        "流程图数据不完整：连线 [%s] 缺少 %s 属性。"
                                + "请在流程设计器中检查所有连线是否完整连接到目标节点，重新保存后再部署。",
                        flowId, missing));
            }
        }
    }

    static void validateBpmnStructure(String bpmnXml) {
        BpmnXmlUtils.BpmnAnalysis analysis = analyzeSingleProcess(bpmnXml);
        if (analysis.nodes().stream().noneMatch(
                node -> node.topLevel() && "startEvent".equals(node.type()))) {
            throw new RuntimeException("流程模型缺少开始节点，请至少配置一个开始节点。");
        }
        if (analysis.nodes().stream().noneMatch(
                node -> node.topLevel() && "endEvent".equals(node.type()))) {
            throw new RuntimeException("流程模型缺少结束节点，请至少配置一个结束节点。");
        }
        Set<String> nodeIds = analysis.nodes().stream()
                .map(BpmnXmlUtils.BpmnNodeInfo::id)
                .filter(FlowModelBpmnPreflight::hasText)
                .collect(Collectors.toSet());
        for (BpmnXmlUtils.BpmnSequenceFlowInfo flow : analysis.sequenceFlows()) {
            String source = flow.sourceRef();
            String target = flow.targetRef();
            if (source == null || target == null || !nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw new RuntimeException("流程模型存在悬空连线，请检查 sourceRef 和 targetRef 是否指向有效节点。");
            }
        }
    }

    static void validateExecutableNodesAndGatewayConditions(String bpmnXml) {
        BpmnXmlUtils.BpmnAnalysis analysis = analyzeSingleProcess(bpmnXml);
        for (BpmnXmlUtils.BpmnNodeInfo node : analysis.nodes()) {
            String type = node.type().toLowerCase(Locale.ROOT);
            String id = node.id();
            String name = node.name();
            String label = (name == null || name.isBlank()) ? id : name;
            if ("scriptTask".equalsIgnoreCase(type)
                    || "callActivity".equalsIgnoreCase(type)
                    || "subProcess".equalsIgnoreCase(type)
                    || "transaction".equalsIgnoreCase(type)
                    || "adHocSubProcess".equalsIgnoreCase(type)) {
                throw new RuntimeException(String.format(
                        "节点 [%s] 使用了暂不支持的执行类型 [%s]，请改用用户任务或受支持的抄送节点。",
                        label == null ? "未命名" : label, type));
            }
            if ("serviceTask".equalsIgnoreCase(type)
                    && !"cc".equalsIgnoreCase(node.attribute("type"))) {
                throw new RuntimeException(String.format(
                        "节点 [%s] 的 serviceTask 未声明受支持的 flowable:type=cc，无法保证运行时执行委托。",
                        label == null ? "未命名" : label));
            }
            if ("serviceTask".equalsIgnoreCase(type)
                    && containsUnsupportedExecutionAttribute(node)) {
                throw new RuntimeException(String.format(
                        "节点 [%s] 包含未注册的执行委托属性，禁止通过 raw XML 绕过执行白名单。",
                        label == null ? "未命名" : label));
            }
            if ("userTask".equalsIgnoreCase(type)) {
                boolean hasAssignee = hasText(node.attribute("assignee"));
                boolean hasCandidateUsers = hasText(node.attribute("candidateUsers"));
                boolean hasCandidateGroups = hasText(node.attribute("candidateGroups"));
                if (!hasAssignee && !hasCandidateUsers && !hasCandidateGroups) {
                    throw new RuntimeException(String.format(
                            "审批节点 [%s] 未配置处理人、候选用户或候选组，请先完成审批人配置。",
                            label == null ? "未命名" : label));
                }
            }
        }
        for (BpmnXmlUtils.BpmnNodeInfo gateway : analysis.nodes()) {
            if (!"exclusiveGateway".equals(gateway.type()) && !"inclusiveGateway".equals(gateway.type())) {
                continue;
            }
            String gatewayId = gateway.id();
            if (gatewayId == null) {
                continue;
            }
            String defaultFlow = gateway.attribute("default");
            int outgoingCount = 0;
            for (BpmnXmlUtils.BpmnSequenceFlowInfo flow : analysis.sequenceFlows()) {
                if (!gatewayId.equals(flow.sourceRef())) {
                    continue;
                }
                outgoingCount++;
                if (flow.hasCondition() && Objects.equals(defaultFlow, flow.id())) {
                    throw new RuntimeException(String.format(
                            "网关 [%s] 的默认分支 [%s] 禁止配置条件表达式。", gatewayId, flow.id()));
                }
                if (!flow.hasCondition() && !Objects.equals(defaultFlow, flow.id())) {
                    throw new RuntimeException(String.format(
                            "网关 [%s] 的分支连线 [%s] 缺少条件表达式或默认分支。",
                            gatewayId, hasText(flow.id()) ? flow.id() : "未命名"));
                }
            }
            if (outgoingCount > 1 && defaultFlow == null) {
                log.debug("网关 [{}] 未声明 default，但所有出口均已配置条件表达式", gatewayId);
            }
        }
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String normalizeBpmnXml(String bpmnXml, String operation) {
        BpmnXmlUtils.NormalizationResult result = BpmnXmlUtils.normalizeDuplicateSequenceFlows(bpmnXml);
        if (result.hasRepairs()) {
            String repairSummary = result.getRepairs().stream()
                    .map(repair -> String.format("%s->%s 保留 [%s] 删除 %s",
                            repair.getSourceRef(), repair.getTargetRef(), repair.getKeptFlowId(),
                            repair.getRemovedFlowIds()))
                    .collect(Collectors.joining("; "));
            log.warn("{}：已自动清理 BPMN 重复连线，{}", operation, repairSummary);
        }
        BpmnXmlUtils.LegacyMultiInstanceNormalizationResult multiInstanceResult =
                BpmnXmlUtils.normalizeLegacyMultiInstanceExpressions(result.getBpmnXml());
        if (multiInstanceResult.hasRepairs()) {
            log.warn("{}：已自动清理 BPMN 旧版会签表达式，节点={}",
                    operation, multiInstanceResult.getNormalizedNodeIds());
        }
        return multiInstanceResult.getBpmnXml();
    }

    private static boolean containsUnsupportedExecutionAttribute(BpmnXmlUtils.BpmnNodeInfo node) {
        return hasText(node.attribute("class"))
                || hasText(node.attribute("delegateExpression"))
                || hasText(node.attribute("expression"));
    }

    private static BpmnXmlUtils.BpmnAnalysis analyzeSingleProcess(String bpmnXml) {
        BpmnXmlUtils.BpmnAnalysis analysis = BpmnXmlUtils.analyze(bpmnXml);
        if (analysis.processCount() != 1) {
            throw new RuntimeException("BPMN XML 必须包含且只能包含一个 process 节点");
        }
        return analysis;
    }
}
