package com.mdframe.forge.starter.flow.service.impl;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FlowStarterContextRequirementResolverTest {

    @Test
    void detectsOnlyContextVariablesReferencedByBpmn() {
        BpmnModel model = processModel(
                "${initiatorLeader}",
                List.of("${startUserRoleIds}"),
                "${regionCode != null && startUserOrgIds != null && startUserActiveOrgId != null}");

        FlowStarterContextRequirementResolver.Requirements requirements =
                new FlowStarterContextRequirementResolver().resolve("definition-1", model);

        assertThat(requirements.leader()).isTrue();
        assertThat(requirements.region()).isTrue();
        assertThat(requirements.roles()).isTrue();
        assertThat(requirements.activeOrg()).isTrue();
        assertThat(requirements.organizations()).isTrue();
    }

    @Test
    void unrelatedBpmnDoesNotRequestOrganizationContext() {
        BpmnModel model = processModel("42", List.of("finance"), "${amount > 1000}");

        FlowStarterContextRequirementResolver.Requirements requirements =
                new FlowStarterContextRequirementResolver().resolve("definition-2", model);

        assertThat(requirements.any()).isFalse();
    }

    @Test
    void eitherRegionVariableRequestsRegionContext() {
        FlowStarterContextRequirementResolver resolver = new FlowStarterContextRequirementResolver(
                ignored -> "<condition>${startUserRegionCode != null}</condition>", 512);

        FlowStarterContextRequirementResolver.Requirements requirements =
                resolver.resolve("definition-region", new BpmnModel());

        assertThat(requirements.region()).isTrue();
        assertThat(requirements.leader()).isFalse();
    }

    @Test
    void missingOrUnreadableModelFallsBackToAllContext() {
        FlowStarterContextRequirementResolver failingResolver = new FlowStarterContextRequirementResolver(
                ignored -> {
                    throw new IllegalStateException("broken bpmn");
                }, 512);

        assertThat(new FlowStarterContextRequirementResolver().resolve("missing", null).all()).isTrue();
        assertThat(failingResolver.resolve("broken", new BpmnModel()).all()).isTrue();
    }

    @Test
    void opaqueDelegateFallsBackToAllContextBecauseCodeMayReadHiddenVariables() {
        FlowStarterContextRequirementResolver resolver = new FlowStarterContextRequirementResolver(
                ignored -> "<serviceTask flowable:class=\"com.example.ApprovalDelegate\"/>", 512);

        assertThat(resolver.resolve("delegate", new BpmnModel()).all()).isTrue();
    }

    @Test
    void immutableDefinitionIdIsParsedOnceAndCacheStaysBounded() {
        AtomicInteger extractionCount = new AtomicInteger();
        FlowStarterContextRequirementResolver resolver = new FlowStarterContextRequirementResolver(
                ignored -> {
                    extractionCount.incrementAndGet();
                    return "<assignee>${initiatorLeader}</assignee>";
                }, 3);
        BpmnModel model = new BpmnModel();

        resolver.resolve("definition-1", model);
        resolver.resolve("definition-1", model);
        resolver.resolve("definition-2", model);
        resolver.resolve("definition-3", model);
        resolver.resolve("definition-4", model);

        assertThat(extractionCount).hasValue(4);
        assertThat(resolver.cacheSize()).isLessThanOrEqualTo(3);
    }

    private static BpmnModel processModel(String assignee, List<String> candidateUsers,
                                          String conditionExpression) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("approval");
        model.addProcess(process);

        StartEvent start = new StartEvent();
        start.setId("start");
        UserTask task = new UserTask();
        task.setId("approve");
        task.setAssignee(assignee);
        task.setCandidateUsers(candidateUsers);
        EndEvent end = new EndEvent();
        end.setId("end");

        SequenceFlow toTask = new SequenceFlow("start", "approve");
        toTask.setId("toApprove");
        toTask.setConditionExpression(conditionExpression);
        SequenceFlow toEnd = new SequenceFlow("approve", "end");
        toEnd.setId("toEnd");

        process.addFlowElement(start);
        process.addFlowElement(task);
        process.addFlowElement(end);
        process.addFlowElement(toTask);
        process.addFlowElement(toEnd);
        return model;
    }
}
