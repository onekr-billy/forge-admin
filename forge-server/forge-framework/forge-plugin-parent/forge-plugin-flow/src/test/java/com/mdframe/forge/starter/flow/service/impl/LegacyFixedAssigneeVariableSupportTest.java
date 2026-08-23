package com.mdframe.forge.starter.flow.service.impl;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyFixedAssigneeVariableSupportTest {

    @Test
    void injectsLegacyFixedUserExpressionsWithoutChangingDynamicAssignees() {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("legacy_assignee");
        process.addFlowElement(userTask("fixed", "${user_45}"));
        process.addFlowElement(userTask("dynamic", "${deptManager}"));
        process.addFlowElement(userTask("spel", "${userService.findById(ownerId)}"));
        SubProcess nested = new SubProcess();
        nested.setId("nested");
        nested.addFlowElement(userTask("nestedFixed", "${user_2090384244139360257}"));
        process.addFlowElement(nested);
        model.addProcess(process);
        Map<String, Object> variables = new HashMap<>();
        variables.put("user_45", "999");

        int injected = LegacyFixedAssigneeVariableSupport.enrich(model, variables);

        assertThat(injected).isEqualTo(2);
        assertThat(variables)
                .containsEntry("user_45", "45")
                .containsEntry("user_2090384244139360257", "2090384244139360257")
                .doesNotContainKeys("deptManager", "userService");
    }

    private UserTask userTask(String id, String assignee) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setAssignee(assignee);
        return task;
    }
}
