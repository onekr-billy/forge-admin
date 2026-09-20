package com.mdframe.forge.starter.flow.service.impl;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("flow task return direct-send state changes")
class FlowTaskServiceImplStateChangeTest {

    private FlowTaskServiceImpl service;
    private RuntimeService runtimeService;
    private Method directSendAfterReturn;
    private Method mergeActionVariables;

    @BeforeEach
    void setUp() throws Exception {
        service = new FlowTaskServiceImpl();
        runtimeService = mock(RuntimeService.class);
        Field runtimeServiceField = FlowTaskServiceImpl.class.getDeclaredField("runtimeService");
        runtimeServiceField.setAccessible(true);
        runtimeServiceField.set(service, runtimeService);
        directSendAfterReturn = FlowTaskServiceImpl.class.getDeclaredMethod(
                "directSendAfterReturn", Task.class, Map.class, String.class);
        directSendAfterReturn.setAccessible(true);
        mergeActionVariables = FlowTaskServiceImpl.class.getDeclaredMethod(
                "mergeActionVariables", Map.class, boolean.class);
        mergeActionVariables.setAccessible(true);
    }

    @Test
    @DisplayName("direct-send rejects multiple active branches without merging them")
    void directSendRejectsMultipleActiveBranches() {
        Task completedTask = returnedTask();
        stubReturnVariables();
        when(runtimeService.getActiveActivityIds("process-1"))
                .thenReturn(List.of("branch-a", "branch-b"));

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> directSendAfterReturn.invoke(
                        service, completedTask, Map.of("directSend", true), "100"));

        assertEquals("当前流程存在多个活动分支，不能安全直送",
                exception.getCause().getMessage());
        verify(runtimeService, never()).createChangeActivityStateBuilder();
    }

    @Test
    @DisplayName("direct-send moves one active activity back to the original approval node")
    void directSendMovesSingleActiveActivity() throws Exception {
        Task completedTask = returnedTask();
        stubReturnVariables();
        when(runtimeService.getActiveActivityIds("process-1"))
                .thenReturn(List.of("next-review"));
        ChangeActivityStateBuilder builder = mock(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);
        when(builder.processInstanceId("process-1")).thenReturn(builder);
        when(builder.moveActivityIdTo("next-review", "original-approve")).thenReturn(builder);

        directSendAfterReturn.invoke(
                service, completedTask, Map.of("directSend", true), "100");

        verify(builder).moveActivityIdTo("next-review", "original-approve");
        verify(builder).changeState();
        // 三枚直送标记收敛为一次批量清除，替代逐 key removeVariable 的 3 次往返
        verify(runtimeService).removeVariables("process-1", List.of(
                "FLOW_RETURN_SOURCE_ACTIVITY_ID", "FLOW_RETURN_TARGET_ACTIVITY_ID",
                "FLOW_RETURN_TO_START_PENDING"));
    }

    @Test
    @DisplayName("process already ended skips direct-send variable reads")
    void endedProcessSkipsDirectSend() throws Exception {
        Task completedTask = returnedTask();
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        directSendAfterReturn.invoke(
                service, completedTask, Map.of("directSend", true), "100");

        verify(runtimeService, never()).getVariables("process-1");
        verify(runtimeService, never()).createChangeActivityStateBuilder();
    }

    @Test
    @DisplayName("every normal task action clears a previous reject-to-start marker")
    @SuppressWarnings("unchecked")
    void normalTaskActionClearsRejectToStartMarker() throws Exception {
        Map<String, Object> variables = (Map<String, Object>) mergeActionVariables.invoke(
                service, Map.of("rejectToStart", true), false);

        assertEquals("reject", variables.get("approvalResult"));
        assertEquals(false, variables.get("approved"));
        assertEquals(false, variables.get("rejectToStart"));
    }

    private Task returnedTask() {
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getTaskDefinitionKey()).thenReturn("fix-node");
        return task;
    }

    private void stubReturnVariables() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(mock(ProcessInstance.class));
        // 实现改为一次 getVariables 全量取回三枚直送标记，mock 随之收敛为单次调用
        when(runtimeService.getVariables("process-1")).thenReturn(Map.of(
                "FLOW_RETURN_SOURCE_ACTIVITY_ID", "original-approve",
                "FLOW_RETURN_TARGET_ACTIVITY_ID", "fix-node",
                "FLOW_RETURN_TO_START_PENDING", false));
    }
}
