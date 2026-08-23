package com.mdframe.forge.starter.flow.service.impl;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowElementsContainer;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 兼容历史流程设计器把固定用户 45 保存成 {@code ${user_45}} 的错误协议。
 *
 * <p>仅识别完整匹配且用户 ID 为数字的表达式，不处理普通流程变量和 SPEL，
 * 避免扩大表达式求值边界。新发布模型应直接使用 {@code assignee="45"}。</p>
 */
final class LegacyFixedAssigneeVariableSupport {

    private static final Pattern LEGACY_FIXED_USER = Pattern.compile("^\\$\\{user_([0-9]+)}$");

    private LegacyFixedAssigneeVariableSupport() {
    }

    static int enrich(BpmnModel model, Map<String, Object> variables) {
        if (model == null || variables == null) {
            return 0;
        }
        int injected = 0;
        for (Process process : model.getProcesses()) {
            injected += enrichContainer(process, variables);
        }
        return injected;
    }

    private static int enrichContainer(FlowElementsContainer container, Map<String, Object> variables) {
        int injected = 0;
        for (FlowElement element : container.getFlowElements()) {
            if (element instanceof UserTask userTask) {
                Matcher matcher = LEGACY_FIXED_USER.matcher(String.valueOf(userTask.getAssignee()));
                if (matcher.matches()) {
                    variables.put("user_" + matcher.group(1), matcher.group(1));
                    injected++;
                }
            }
            if (element instanceof FlowElementsContainer nested) {
                injected += enrichContainer(nested, variables);
            }
        }
        return injected;
    }
}
