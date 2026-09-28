package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.service.ISysUserService;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowInstanceServiceStarterContextTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void unusedOrganizationContextSkipsQueriesAndKeepsCallerVariables() {
        Harness harness = harness(processModel("${amount > 1000}"));
        Map<String, Object> requestedVariables = new HashMap<>();
        requestedVariables.put("amount", 2000);
        requestedVariables.put("startUserOrgIds", "caller-value");

        harness.service().startProcess("approval", "business-1", "expense", "费用审批",
                requestedVariables, "9", "张三", "12", "研发部");

        verify(harness.orgService()).getUserInfo("9");
        verify(harness.orgService(), never()).getLeaderUserIdByLevel(any(), anyInt());
        verify(harness.orgService(), never()).getLeaderUserIds(any());
        verifyNoInteractions(harness.userService());

        Map<String, Object> startedVariables = captureStartedVariables(harness.runtimeService());
        assertThat(startedVariables)
                .containsEntry("amount", 2000)
                .containsEntry("startUserOrgIds", "caller-value")
                .doesNotContainKeys("initiatorLeader", "regionCode", "startUserRoleIds");
    }

    @Test
    void regionExpressionLoadsOnlyUserRegionContext() {
        Harness harness = harness(processModel("${regionCode != null}"));
        SysUser user = new SysUser();
        user.setRegionCode("370100");
        when(harness.userService().selectUserById(9L)).thenReturn(user);

        harness.service().startProcess("approval", "business-2", "expense", "区域审批",
                Map.of(), "9", "张三", "12", "研发部");

        verify(harness.userService()).selectUserById(9L);
        verify(harness.userService(), never()).selectUserOrgIds(any());
        verify(harness.userService(), never()).selectUserOrgRoleIds(any(), any(), any());
        verify(harness.orgService(), never()).getLeaderUserIdByLevel(any(), anyInt());
        verify(harness.orgService(), never()).getLeaderUserIds(any());

        Map<String, Object> startedVariables = captureStartedVariables(harness.runtimeService());
        assertThat(startedVariables)
                .containsEntry("regionCode", "370100")
                .containsEntry("startUserRegionCode", "370100")
                .doesNotContainKeys("initiatorLeader", "startUserRoleIds", "startUserOrgIds");
    }

    @Test
    void trustedSessionRealNameSkipsDuplicateUserLookup() {
        Harness harness = harness(processModel("${amount > 1000}"));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(9L);
        loginUser.setRealName("可信姓名");

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);

            harness.service().startProcess("approval", "business-3", "expense", "费用审批",
                    Map.of(), "9", "请求体姓名", "12", "研发部");
        }

        verify(harness.orgService(), never()).getUserInfo(any());
        ArgumentCaptor<FlowBusiness> business = ArgumentCaptor.forClass(FlowBusiness.class);
        verify(harness.businessMapper()).insert(business.capture());
        assertThat(business.getValue().getApplyUserName()).isEqualTo("可信姓名");
    }

    @SuppressWarnings("unchecked")
    private static Harness harness(BpmnModel model) {
        TenantContextHolder.setTenantId(7L);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        FlowBusinessMapper businessMapper = mock(FlowBusinessMapper.class);
        FlowOrgIntegrationService orgService = mock(FlowOrgIntegrationService.class);
        ISysUserService userService = mock(ISysUserService.class);

        when(processEngine.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionKey("approval")).thenReturn(definitionQuery);
        when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn("approval:1:definition");
        when(definition.getKey()).thenReturn("approval");
        when(repositoryService.getBpmnModel("approval:1:definition")).thenReturn(model);
        when(runtimeService.startProcessInstanceById(
                eq("approval:1:definition"), any(), any(Map.class)))
                .thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(orgService.getUserInfo("9")).thenReturn(Map.of("realName", "张三"));

        FlowInstanceServiceImpl service = new FlowInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "processEngine", processEngine);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "flowBusinessMapper", businessMapper);
        ReflectionTestUtils.setField(service, "flowOrgIntegrationService", orgService);
        ReflectionTestUtils.setField(service, "sysUserService", userService);
        ReflectionTestUtils.setField(service, "flowErrorLogService", mock(FlowErrorLogService.class));
        return new Harness(service, runtimeService, businessMapper, orgService, userService);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> captureStartedVariables(RuntimeService runtimeService) {
        ArgumentCaptor<Map> variables = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceById(
                eq("approval:1:definition"), any(), variables.capture());
        return (Map<String, Object>) variables.getValue();
    }

    private static BpmnModel processModel(String conditionExpression) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("approval");
        model.addProcess(process);

        StartEvent start = new StartEvent();
        start.setId("start");
        UserTask task = new UserTask();
        task.setId("approve");
        task.setAssignee("42");
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

    private record Harness(FlowInstanceServiceImpl service,
                           RuntimeService runtimeService,
                           FlowBusinessMapper businessMapper,
                           FlowOrgIntegrationService orgService,
                           ISysUserService userService) {
    }
}
