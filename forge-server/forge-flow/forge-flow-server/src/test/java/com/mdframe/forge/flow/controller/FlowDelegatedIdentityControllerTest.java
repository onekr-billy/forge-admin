package com.mdframe.forge.flow.controller;

import com.mdframe.forge.flow.dto.FlowInstanceStartDTO;
import com.mdframe.forge.flow.dto.FlowInstanceTerminateDTO;
import com.mdframe.forge.flow.dto.FlowTaskApproveDTO;
import com.mdframe.forge.flow.dto.FlowTaskRejectDTO;
import com.mdframe.forge.starter.auth.config.FlowDelegationSessionVerifier;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.flow.service.FlowInstanceService;
import com.mdframe.forge.starter.flow.service.FlowMonitorService;
import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import com.mdframe.forge.starter.flow.service.FlowOverdueReminderService;
import com.mdframe.forge.starter.flow.service.FlowTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowDelegatedIdentityControllerTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldStartAsTrustedSessionUserAndIgnoreBodyIdentity() {
        FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
        when(flowInstanceService.startProcess(
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("process-1");
        FlowDelegationSessionVerifier delegationVerifier = mock(FlowDelegationSessionVerifier.class);
        FlowInstanceController controller = new FlowInstanceController(
                flowInstanceService, mock(FlowMonitorService.class), mock(FlowOrgIntegrationService.class),
                delegationVerifier);
        FlowInstanceStartDTO request = new FlowInstanceStartDTO();
        request.setBusinessKey("order:1001");
        request.setBusinessType("order");
        request.setTitle("采购审批");
        request.setVariables(Map.of("amount", 100));
        request.setUserId("999999");
        request.setUserName("伪造用户");
        request.setDeptId("888888");

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            controller.startDelegated("order_approval", request);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(flowInstanceService).startProcess(
                eq("order_approval"), eq("order:1001"), eq("order"), eq("采购审批"),
                variables.capture(), eq("101"), eq("用户A"), eq("201"), eq("研发一部"));
        verify(delegationVerifier).requireTrustedDelegation();
        assertThat(variables.getValue()).containsEntry("amount", 100)
                .doesNotContainKeys("userId", "userName", "deptId", "deptName");
    }

    @Test
    void shouldRejectTaskBodyIdentityThatDiffersFromTrustedSession() {
        FlowTaskService flowTaskService = mock(FlowTaskService.class);
        FlowTaskController controller = new FlowTaskController(
                flowTaskService, mock(FlowOverdueReminderService.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            FlowTaskApproveDTO request = new FlowTaskApproveDTO();
            request.setTaskId("task-1");
            request.setUserId("202");
            request.setTenantId(1L);
            assertThatThrownBy(() -> controller.approve(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FLOW_TASK_ASSIGNEE_MISMATCH");
        }

        verify(flowTaskService, never()).approve(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPassTrustedTenantAndUserToTaskService() {
        FlowTaskService flowTaskService = mock(FlowTaskService.class);
        FlowTaskController controller = new FlowTaskController(
                flowTaskService, mock(FlowOverdueReminderService.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            FlowTaskRejectDTO request = new FlowTaskRejectDTO();
            request.setTaskId("task-1");
            request.setUserId("101");
            request.setTenantId(1L);
            request.setComment("不同意");
            request.setIdempotencyKey("flow-action-key-1001");
            request.setRequestDigest("sha256:digest");
            controller.reject(request);
        }

        verify(flowTaskService).reject(
                "task-1", "101", "不同意", null, 1L,
                "flow-action-key-1001", "sha256:digest");
    }

    @Test
    void shouldQueryTodoAsTrustedSessionUserAndCapPageSize() {
        FlowTaskService flowTaskService = mock(FlowTaskService.class);
        FlowTaskController controller = new FlowTaskController(
                flowTaskService, mock(FlowOverdueReminderService.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            controller.todo(1, 500, "101", null, null, null);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.extension.plugins.pagination.Page> pageCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class);
        verify(flowTaskService).todoTasks(pageCaptor.capture(), eq("101"), isNull(), isNull(), isNull());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
    }

    @Test
    void shouldRejectTodoIdentityThatDiffersFromTrustedSession() {
        FlowTaskService flowTaskService = mock(FlowTaskService.class);
        FlowTaskController controller = new FlowTaskController(
                flowTaskService, mock(FlowOverdueReminderService.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertThatThrownBy(() -> controller.todo(1, 10, "202", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FLOW_TASK_ASSIGNEE_MISMATCH");
        }

        verify(flowTaskService, never()).todoTasks(any(), any(), any(), any(), any());
    }

    @Test
    void shouldClaimAsTrustedSessionUser() {
        FlowTaskService flowTaskService = mock(FlowTaskService.class);
        FlowTaskController controller = new FlowTaskController(
                flowTaskService, mock(FlowOverdueReminderService.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            controller.claim("task-1", "101");
        }

        verify(flowTaskService).claimTask("task-1", "101");
    }

    @Test
    void shouldTerminateAndDeleteProcessAsTrustedSessionUser() {
        FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
        FlowInstanceController controller = new FlowInstanceController(
                flowInstanceService, mock(FlowMonitorService.class), mock(FlowOrgIntegrationService.class),
                mock(FlowDelegationSessionVerifier.class));
        FlowInstanceTerminateDTO request = new FlowInstanceTerminateDTO();
        request.setUserId("101");
        request.setReason("取消");

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            controller.terminate("order:1001", request);
            controller.delete("order:1001", "101");
        }

        verify(flowInstanceService).terminateProcess("order:1001", "101", "取消");
        verify(flowInstanceService).deleteProcess("order:1001", "101");
    }

    @Test
    void shouldRejectDeleteProcessIdentityThatDiffersFromTrustedSession() {
        FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
        FlowInstanceController controller = new FlowInstanceController(
                flowInstanceService, mock(FlowMonitorService.class), mock(FlowOrgIntegrationService.class),
                mock(FlowDelegationSessionVerifier.class));

        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertThatThrownBy(() -> controller.delete("order:1001", "202"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("FLOW_TASK_ASSIGNEE_MISMATCH");
        }

        verify(flowInstanceService, never()).deleteProcess(any(), any());
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setRealName("用户A");
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        user.setActiveOrgName("研发一部");
        user.setPermissions(Set.of("flow:task:approve", "flow:task:reject"));
        return new ExecutionIdentity(user, "USER", 101L, 999L,
                301L, "agent-client", "token-1", Set.of("capability:invoke"));
    }

}
