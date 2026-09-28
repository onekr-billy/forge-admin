package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowTaskStatus;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowTaskReturnIdempotencyTest {

    @Test
    void completedReturnReplaysBeforeAccessingMissingRuntimeTask() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowTask storedTask = new FlowTask();
        storedTask.setTenantId(1L);
        storedTask.setAssignee("101");
        storedTask.setStatus(FlowTaskStatus.RETURNED.getCode());
        storedTask.setActionType("RETURN");
        storedTask.setActionIdempotencyKey("return-key-1");
        storedTask.setActionRequestDigest("sha256:return-digest");
        when(taskMapper.selectByTaskIdForUpdateAndTenant("task-1", 1L)).thenReturn(storedTask);
        AtomicBoolean tenantGuardCalled = new AtomicBoolean();
        FlowTaskActionCoordinator coordinator = new FlowTaskActionCoordinator(
                runtimeService,
                taskService,
                mock(RepositoryService.class),
                mock(HistoryService.class),
                taskMapper,
                mock(FlowBusinessMapper.class),
                mock(FlowErrorLogService.class),
                null,
                null,
                (taskId, userId, allowInitiator) -> { },
                taskId -> tenantGuardCalled.set(true),
                userId -> { },
                (task, userId) -> false);

        coordinator.returnTask("task-1", "101", "退回修改", null, "draft-node",
                1L, "return-key-1", "sha256:return-digest");

        verify(taskMapper).selectByTaskIdForUpdateAndTenant("task-1", 1L);
        assertFalse(tenantGuardCalled.get());
        verifyNoInteractions(taskService, runtimeService);
    }
}
