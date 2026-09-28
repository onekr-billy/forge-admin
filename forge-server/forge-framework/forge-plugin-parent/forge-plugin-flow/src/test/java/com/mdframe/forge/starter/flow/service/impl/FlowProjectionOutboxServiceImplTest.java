package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import com.mdframe.forge.starter.flow.event.FlowProjectionEvent;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.mapper.FlowProjectionOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowProjectionOutboxServiceImplTest {

    @Test
    void appendPersistsVersionedTenantSnapshotAndVerifiesDigest() {
        FlowProjectionOutboxMapper mapper = mock(FlowProjectionOutboxMapper.class);
        doAnswer(invocation -> {
            FlowProjectionOutbox outbox = invocation.getArgument(0);
            outbox.setId(17L);
            return 1;
        }).when(mapper).insert(any(FlowProjectionOutbox.class));
        FlowProjectionOutboxServiceImpl service = service(mapper);

        FlowTask task = task();
        FlowProjectionOutbox outbox = service.append(
                FlowProjectionEvent.task(FlowProjectionType.TASK_CREATED, task));
        FlowProjectionOutboxPayload payload = service.deserialize(outbox);

        assertEquals(17L, outbox.getId());
        assertEquals(7L, outbox.getTenantId());
        assertEquals("TASK", outbox.getAggregateType());
        assertEquals("task-1", payload.getTask().getTaskId());
        assertEquals(64, outbox.getPayloadHash().length());
    }

    @Test
    void deserializeRejectsTamperedSnapshot() {
        FlowProjectionOutboxMapper mapper = mock(FlowProjectionOutboxMapper.class);
        doAnswer(invocation -> {
            FlowProjectionOutbox outbox = invocation.getArgument(0);
            outbox.setId(18L);
            return 1;
        }).when(mapper).insert(any(FlowProjectionOutbox.class));
        FlowProjectionOutboxServiceImpl service = service(mapper);
        FlowProjectionOutbox outbox = service.append(
                FlowProjectionEvent.task(FlowProjectionType.TASK_CREATED, task()));
        outbox.setPayload(outbox.getPayload() + " ");

        assertThrows(IllegalStateException.class, () -> service.deserialize(outbox));
    }

    @Test
    void failedClaimUsesBackoffAndDeadLetterBoundary() {
        FlowProjectionOutboxMapper mapper = mock(FlowProjectionOutboxMapper.class);
        FlowProjectionOutboxServiceImpl service = service(mapper);
        FlowProjectionOutbox outbox = new FlowProjectionOutbox();
        outbox.setId(19L);
        outbox.setTenantId(7L);
        outbox.setRetryCount(3);
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 6, 0);
        when(mapper.markFailed(eq(7L), eq(19L), eq("worker"), eq(4), eq(null),
                eq("IllegalStateException"), eq(now))).thenReturn(1);

        service.markFailed(outbox, "worker", new IllegalStateException("sensitive"),
                now, 3, Duration.ofSeconds(10));

        verify(mapper).markFailed(7L, 19L, "worker", 4, null,
                "IllegalStateException", now);
    }

    private FlowTask task() {
        FlowTask task = new FlowTask();
        task.setTenantId(7L);
        task.setTaskId("task-1");
        task.setProcessInstanceId("process-1");
        task.setTitle("审批任务");
        return task;
    }

    private FlowProjectionOutboxServiceImpl service(FlowProjectionOutboxMapper mapper) {
        return new FlowProjectionOutboxServiceImpl(
                mapper, new ObjectMapper().registerModule(new JavaTimeModule()));
    }
}
