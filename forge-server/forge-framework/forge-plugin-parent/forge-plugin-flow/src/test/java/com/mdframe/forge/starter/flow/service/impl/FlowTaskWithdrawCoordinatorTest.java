package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.enums.FlowBusinessStatus;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowTaskWithdrawCoordinatorTest {

    @Test
    void withdrawDeletesRuntimeProcessAndPersistsFinalCommandResult() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowBusinessMapper businessMapper = mock(FlowBusinessMapper.class);
        FlowErrorLogService errorLogService = mock(FlowErrorLogService.class);
        FlowTaskNodePolicy nodePolicy = mock(FlowTaskNodePolicy.class);
        ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        TaskQuery taskQuery = mock(TaskQuery.class);
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        business.setApplyUserId("101");
        business.setStatus(FlowBusinessStatus.RUNNING.getCode());

        when(businessMapper.selectByProcessInstanceIdAndTenantIdForUpdate("process-1", 1L))
                .thenReturn(business);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId("process-1")).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("definition-1");
        when(nodePolicy.readBooleanProcessAttribute("definition-1", "allowSubmitterWithdraw"))
                .thenReturn(true);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());
        when(businessMapper.markWithdrawn(
                eq(1L), eq("process-1"), eq(FlowBusinessStatus.CANCELED.getCode()),
                any(LocalDateTime.class), eq("flow:withdraw-key-1"), eq("withdraw-digest-1"),
                eq("WITHDRAW")))
                .thenReturn(1);
        FlowTaskWithdrawCoordinator coordinator = new FlowTaskWithdrawCoordinator(
                runtimeService, taskService, taskMapper, businessMapper, errorLogService, nodePolicy);

        coordinator.withdraw("process-1", "101", "申请人撤回", 1L,
                "flow:withdraw-key-1", "withdraw-digest-1");

        verify(runtimeService).deleteProcessInstance("process-1", "用户撤回");
        verify(businessMapper).markWithdrawn(
                eq(1L), eq("process-1"), eq(FlowBusinessStatus.CANCELED.getCode()),
                any(LocalDateTime.class), eq("flow:withdraw-key-1"), eq("withdraw-digest-1"),
                eq("WITHDRAW"));
        verifyNoInteractions(taskMapper, errorLogService);
    }

    @Test
    void completedWithdrawReplaysBeforeAccessingMissingRuntimeProcess() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowBusinessMapper businessMapper = mock(FlowBusinessMapper.class);
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        business.setApplyUserId("101");
        business.setStatus(FlowBusinessStatus.CANCELED.getCode());
        business.setActionType("WITHDRAW");
        business.setActionIdempotencyKey("flow:withdraw-key-1");
        business.setActionRequestDigest("withdraw-digest-1");
        when(businessMapper.selectByProcessInstanceIdAndTenantIdForUpdate("process-1", 1L))
                .thenReturn(business);
        FlowErrorLogService errorLogService = mock(FlowErrorLogService.class);
        FlowTaskWithdrawCoordinator coordinator = new FlowTaskWithdrawCoordinator(
                runtimeService, taskService, taskMapper, businessMapper,
                errorLogService, mock(FlowTaskNodePolicy.class));

        coordinator.withdraw("process-1", "101", "申请人撤回", 1L,
                "flow:withdraw-key-1", "withdraw-digest-1");

        verify(businessMapper).selectByProcessInstanceIdAndTenantIdForUpdate("process-1", 1L);
        verify(businessMapper, never()).markWithdrawn(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(runtimeService, taskService, taskMapper, errorLogService);
    }
}
