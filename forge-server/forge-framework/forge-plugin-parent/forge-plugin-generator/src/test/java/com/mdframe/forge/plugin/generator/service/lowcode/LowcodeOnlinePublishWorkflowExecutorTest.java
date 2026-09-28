package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowcodeOnlinePublishWorkflowExecutorTest {

    private LowcodePublishTaskService taskService;
    private LowcodeDdlService ddlService;
    private LowcodeOnlinePublishConfigActionService configActionService;
    private LowcodePublishPostActionService postActionService;
    private LowcodeOnlinePublishWorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        taskService = mock(LowcodePublishTaskService.class);
        ddlService = mock(LowcodeDdlService.class);
        configActionService = mock(LowcodeOnlinePublishConfigActionService.class);
        postActionService = mock(LowcodePublishPostActionService.class);
        executor = new LowcodeOnlinePublishWorkflowExecutor(
                taskService, ddlService, configActionService,
                postActionService, new ObjectMapper());
    }

    @Test
    void requestPathPersistsDdlThenConfigAndDefersPostSync() {
        AiLowcodePublishTask task = task("DDL_PENDING");
        LowcodeOnlinePublishCommand command = command();
        when(taskService.restoreOnlinePublish(task)).thenReturn(command);
        doAnswer(invocation -> {
            task.setCurrentStage(invocation.getArgument(2));
            return null;
        }).when(taskService).advanceStage(task, "DDL_PENDING", "CONFIG_PENDING");
        when(configActionService.execute(command))
                .thenReturn(LowcodeOnlinePublishConfigActionService.Result.COMPLETED);

        LowcodeOnlinePublishWorkflowExecutor.Result result = executor.execute(task, true);

        assertEquals(LowcodeOnlinePublishWorkflowExecutor.Result.DEFERRED, result);
        InOrder order = inOrder(ddlService, taskService, configActionService);
        order.verify(ddlService).executeCreateTableWithoutTransaction(
                org.mockito.ArgumentMatchers.any());
        order.verify(taskService).advanceStage(task, "DDL_PENDING", "CONFIG_PENDING");
        order.verify(configActionService).execute(command);
        order.verify(taskService).releaseStage(task, "CONFIG_PENDING", "POST_SYNC");
        verify(postActionService, never()).execute(command.toPostCommand());
    }

    @Test
    void staleConfigStopsBeforePostSync() {
        AiLowcodePublishTask task = task("CONFIG_PENDING");
        LowcodeOnlinePublishCommand command = command();
        when(taskService.restoreOnlinePublish(task)).thenReturn(command);
        when(configActionService.execute(command))
                .thenReturn(LowcodeOnlinePublishConfigActionService.Result.SUPERSEDED);

        LowcodeOnlinePublishWorkflowExecutor.Result result = executor.execute(task, false);

        assertEquals(LowcodeOnlinePublishWorkflowExecutor.Result.SUPERSEDED, result);
        verify(postActionService, never()).execute(command.toPostCommand());
    }

    @Test
    void configFailureLeavesPersistedConfigStageForRetry() {
        AiLowcodePublishTask task = task("CONFIG_PENDING");
        LowcodeOnlinePublishCommand command = command();
        when(taskService.restoreOnlinePublish(task)).thenReturn(command);
        when(configActionService.execute(command))
                .thenThrow(new IllegalStateException("transient database failure"));

        assertThrows(IllegalStateException.class, () -> executor.execute(task, false));

        assertEquals("CONFIG_PENDING", task.getCurrentStage());
        verify(ddlService, never()).executeCreateTableWithoutTransaction(
                org.mockito.ArgumentMatchers.any());
        verify(taskService, never()).advanceStage(
                task, "CONFIG_PENDING", "POST_SYNC");
        verify(taskService, never()).releaseStage(
                task, "CONFIG_PENDING", "POST_SYNC");
        verify(postActionService, never()).execute(command.toPostCommand());
    }

    @Test
    void recoveredPostSyncUsesThePersistedCommandIdentity() {
        AiLowcodePublishTask task = task("POST_SYNC");
        LowcodeOnlinePublishCommand command = command();
        when(taskService.restoreOnlinePublish(task)).thenReturn(command);
        when(postActionService.execute(command.toPostCommand()))
                .thenReturn(LowcodePublishPostActionService.Result.COMPLETED);

        LowcodeOnlinePublishWorkflowExecutor.Result result = executor.execute(task, false);

        assertEquals(LowcodeOnlinePublishWorkflowExecutor.Result.COMPLETED, result);
    }

    private AiLowcodePublishTask task(String stage) {
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(30L);
        task.setTenantId(7L);
        task.setCurrentStage(stage);
        task.setTaskStatus("PROCESSING");
        task.setLockOwner("worker-1");
        return task;
    }

    private LowcodeOnlinePublishCommand command() {
        LowcodeOnlinePublishConfigSnapshot snapshot = new LowcodeOnlinePublishConfigSnapshot();
        snapshot.setModelSchema("{\"tableName\":\"biz_order\",\"fields\":[]}");
        snapshot.setPageSchema("{}");
        return new LowcodeOnlinePublishCommand(
                1, 7L, 10L, "orders", 4, 2, 20L, 3, 42L,
                "digest", snapshot, true, 99L,
                "sales", "order", "订单", "在线发布");
    }
}
