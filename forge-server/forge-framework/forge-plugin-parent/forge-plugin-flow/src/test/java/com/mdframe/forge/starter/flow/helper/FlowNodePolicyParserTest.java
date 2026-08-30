package com.mdframe.forge.starter.flow.helper;

import com.mdframe.forge.starter.flow.dto.FlowApprovalPointDTO;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowNodePolicyParserTest {

    @Test
    void shouldParseStructuredApprovalPoints() {
        UserTask task = new UserTask();
        addFlowableAttribute(task, "responsibilityDescription", "核对合同法律及财务风险");
        addFlowableAttribute(task, "approvalPoints", """
                [
                  {"id":"optional-1","content":"核对一般条款","required":false,"sort":2},
                  {"id":"required-1","content":"核对违约责任","required":true,"sort":1}
                ]
                """);

        List<FlowApprovalPointDTO> points = FlowNodePolicyParser.resolveApprovalPoints(task);

        assertEquals("核对合同法律及财务风险",
                FlowNodePolicyParser.resolveResponsibilityDescription(task));
        assertEquals(List.of("required-1", "optional-1"),
                points.stream().map(FlowApprovalPointDTO::getId).toList());
        assertTrue(points.get(0).getRequired());
        assertFalse(points.get(1).getRequired());
    }

    @Test
    void shouldFallbackToLegacyResponsibility() {
        UserTask task = new UserTask();
        addFlowableAttribute(task, "responsibility", "核对付款条件");

        List<FlowApprovalPointDTO> points = FlowNodePolicyParser.resolveApprovalPoints(task);

        assertEquals(1, points.size());
        assertEquals("legacy-responsibility", points.get(0).getId());
        assertFalse(points.get(0).getRequired());
    }

    private void addFlowableAttribute(UserTask task, String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute(name, value);
        attribute.setNamespace(FlowNodePolicyParser.FLOWABLE_NS);
        attribute.setNamespacePrefix("flowable");
        task.addAttribute(attribute);
    }
}
