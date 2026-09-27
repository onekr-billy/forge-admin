package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowModelBpmnPreflightTest {

    @Test
    void validMinimalProcessPassesAllChecksWithSingleQuotesAndReorderedAttributes() {
        String xml = document("""
                <bpmn:startEvent name='Start' id='start'/>
                <bpmn:userTask flowable:candidateGroups='managers' name='Approve' id='approve'/>
                <bpmn:endEvent id='end'/>
                <bpmn:sequenceFlow targetRef='approve' id='first' sourceRef='start'/>
                <bpmn:sequenceFlow sourceRef='approve' targetRef='end' id='last'/>
                """);

        assertEquals("old", FlowModelBpmnPreflight.extractProcessKey(xml));
        String replaced = FlowModelBpmnPreflight.replaceProcessId(xml, "current");
        assertEquals("current", FlowModelBpmnPreflight.extractProcessKey(replaced));
        assertTrue(replaced.contains("bpmnElement=\"current\""));
        assertDoesNotThrow(() -> FlowModelBpmnPreflight.validateSequenceFlowRefs(replaced));
        assertDoesNotThrow(() -> FlowModelBpmnPreflight.validateBpmnStructure(replaced));
        assertDoesNotThrow(() -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(replaced));
    }

    @Test
    void missingStartAndEndAreRejected() {
        RuntimeException missingStart = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateBpmnStructure(document("<bpmn:endEvent id='end'/>")));
        assertTrue(missingStart.getMessage().contains("缺少开始节点"));
        RuntimeException missingEnd = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateBpmnStructure(document("<bpmn:startEvent id='start'/>")));
        assertTrue(missingEnd.getMessage().contains("缺少结束节点"));
    }

    @Test
    void incompleteAndDanglingSequenceFlowsAreRejected() {
        String incomplete = document("""
                <bpmn:startEvent id='start'/><bpmn:endEvent id='end'/>
                <bpmn:sequenceFlow id='line' sourceRef='start'/>
                """);
        RuntimeException missingTarget = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateSequenceFlowRefs(incomplete));
        assertTrue(missingTarget.getMessage().contains("targetRef"));

        String dangling = document("""
                <bpmn:startEvent id='start'/><bpmn:endEvent id='end'/>
                <bpmn:sequenceFlow id='line' sourceRef='start' targetRef='missing'/>
                """);
        RuntimeException badReference = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateBpmnStructure(dangling));
        assertTrue(badReference.getMessage().contains("悬空连线"));
    }

    @Test
    void unsupportedOrUnassignedExecutionIsRejected() {
        RuntimeException unassigned = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(
                        document("<bpmn:userTask id='approve'/>")));
        assertTrue(unassigned.getMessage().contains("未配置处理人"));
        RuntimeException script = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(
                        document("<bpmn:scriptTask id='script'/>")));
        assertTrue(script.getMessage().contains("暂不支持的执行类型"));
        RuntimeException delegate = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(
                        document("<bpmn:serviceTask id='unsafe' flowable:type='cc' flowable:class='x.Y'/>")));
        assertTrue(delegate.getMessage().contains("未注册的执行委托"));
    }

    @Test
    void gatewayBranchRequiresConditionOrDefaultAndDefaultMustBeUnconditional() {
        String body = """
                <bpmn:exclusiveGateway id='decision' %s/><bpmn:endEvent id='end'/>
                <bpmn:sequenceFlow id='branch' sourceRef='decision' targetRef='end'>%s</bpmn:sequenceFlow>
                """;
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(
                        document(body.formatted("", ""))));
        assertTrue(exception.getMessage().contains("缺少条件表达式或默认分支"));

        assertDoesNotThrow(() -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(
                document(body.formatted("default='branch'", ""))));

        String invalidDefault = document(body.formatted("default='branch'",
                "<bpmn:conditionExpression><![CDATA[${approved}]]></bpmn:conditionExpression>"));
        RuntimeException conditionalDefault = assertThrows(RuntimeException.class,
                () -> FlowModelBpmnPreflight.validateExecutableNodesAndGatewayConditions(invalidDefault));
        assertTrue(conditionalDefault.getMessage().contains("默认分支"));
    }

    @Test
    void multipleProcessesAndDoctypeAreRejected() {
        String multiple = document("<bpmn:startEvent id='start'/><bpmn:endEvent id='end'/>")
                .replace("</bpmn:definitions>", "<bpmn:process id='second'/></bpmn:definitions>");
        assertThrows(RuntimeException.class, () -> FlowModelBpmnPreflight.extractProcessKey(multiple));

        String withDoctype = """
                <?xml version='1.0'?>
                <!DOCTYPE definitions [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>
                <definitions><process id='unsafe'><documentation>&xxe;</documentation></process></definitions>
                """;
        assertThrows(RuntimeException.class, () -> FlowModelBpmnPreflight.extractProcessKey(withDoctype));
    }

    private static String document(String body) {
        return documentWithProcessAttributes("", body);
    }

    private static String documentWithProcessAttributes(String attributes, String body) {
        return """
                <?xml version='1.0' encoding='UTF-8'?>
                <bpmn:definitions xmlns:bpmn='http://www.omg.org/spec/BPMN/20100524/MODEL'
                                  xmlns:bpmndi='http://www.omg.org/spec/BPMN/20100524/DI'
                                  xmlns:flowable='http://flowable.org/bpmn'>
                  <bpmn:process id="old" isExecutable='true' %s>
                    %s
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id='diagram'>
                    <bpmndi:BPMNPlane id='plane' bpmnElement="old"/>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """.formatted(attributes, body);
    }
}
