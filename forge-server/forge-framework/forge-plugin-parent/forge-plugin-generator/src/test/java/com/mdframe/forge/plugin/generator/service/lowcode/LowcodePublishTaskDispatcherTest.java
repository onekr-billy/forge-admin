package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LowcodePublishTaskDispatcherTest {

    private LowcodePublishTaskService taskService;
    private LowcodePublishPostActionService actionService;
    private LowcodeOnlinePublishWorkflowExecutor workflowExecutor;
    private LowcodePublishTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        taskService = mock(LowcodePublishTaskService.class);
        actionService = mock(LowcodePublishPostActionService.class);
        workflowExecutor = mock(LowcodeOnlinePublishWorkflowExecutor.class);
        when(actionService.supportsExecution()).thenReturn(true);
        dispatcher = new LowcodePublishTaskDispatcher(taskService, actionService, workflowExecutor);
    }

    @Test
    void completedActionFinishesClaimedTask() {
        AiLowcodePublishTask candidate = task(1L);
        AiLowcodePublishTask claimed = task(1L);
        LowcodePublishPostCommand command = command();
        when(taskService.claim(eq(candidate), anyString(), any(LocalDateTime.class)))
                .thenReturn(claimed);
        when(taskService.restore(claimed)).thenReturn(command);
        when(actionService.execute(command))
                .thenReturn(LowcodePublishPostActionService.Result.COMPLETED);

        dispatcher.dispatch(candidate);

        verify(taskService).markCompleted(claimed, false);
    }

    @Test
    void supersededActionDoesNotRetryOldVersion() {
        AiLowcodePublishTask candidate = task(2L);
        AiLowcodePublishTask claimed = task(2L);
        LowcodePublishPostCommand command = command();
        when(taskService.claim(eq(candidate), anyString(), any(LocalDateTime.class)))
                .thenReturn(claimed);
        when(taskService.restore(claimed)).thenReturn(command);
        when(actionService.execute(command))
                .thenReturn(LowcodePublishPostActionService.Result.SUPERSEDED);

        dispatcher.dispatch(candidate);

        verify(taskService).markCompleted(claimed, true);
    }

    @Test
    void actionFailureTransitionsClaimedTaskToRetryState() {
        AiLowcodePublishTask candidate = task(3L);
        AiLowcodePublishTask claimed = task(3L);
        LowcodePublishPostCommand command = command();
        IllegalStateException failure = new IllegalStateException("sensitive payload");
        when(taskService.claim(eq(candidate), anyString(), any(LocalDateTime.class)))
                .thenReturn(claimed);
        when(taskService.restore(claimed)).thenReturn(command);
        when(actionService.execute(command)).thenThrow(failure);

        dispatcher.dispatch(candidate);

        verify(taskService).markFailed(claimed, failure);
    }

    @Test
    void processWithoutPublishSynchronizationCapabilityDoesNotScanTasks() {
        when(actionService.supportsExecution()).thenReturn(false);

        dispatcher.dispatch();

        verifyNoInteractions(taskService);
    }

    @Test
    void onlinePublishTaskResumesItsPersistedWorkflowStage() {
        AiLowcodePublishTask candidate = task(4L);
        AiLowcodePublishTask claimed = task(4L);
        when(taskService.claim(eq(candidate), anyString(), any(LocalDateTime.class)))
                .thenReturn(claimed);
        when(taskService.isOnlinePublishTask(claimed)).thenReturn(true);
        when(workflowExecutor.execute(claimed, false))
                .thenReturn(LowcodeOnlinePublishWorkflowExecutor.Result.COMPLETED);

        dispatcher.dispatch(candidate);

        verify(taskService).markCompleted(claimed, false);
    }

    private AiLowcodePublishTask task(Long id) {
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(id);
        task.setRequestId("request-" + id);
        return task;
    }

    private LowcodePublishPostCommand command() {
        return new LowcodePublishPostCommand(
                1, 7L, 10L, "orders", 20L, 3, "PUBLISH",
                true, 99L, null, null, null, 42L);
    }
}
