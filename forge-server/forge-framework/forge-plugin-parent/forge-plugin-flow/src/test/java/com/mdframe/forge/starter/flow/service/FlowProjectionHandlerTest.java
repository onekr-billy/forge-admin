package com.mdframe.forge.starter.flow.service;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.entity.FlowTaskCandidate;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskCandidateMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowProjectionHandlerTest {

    @Test
    void taskCreationUpsertsMirrorAndNormalizedCandidatesWithSequence() {
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowTaskCandidateMapper candidateMapper = mock(FlowTaskCandidateMapper.class);
        FlowBusinessMapper businessMapper = mock(FlowBusinessMapper.class);
        FlowFormInstanceMapper formMapper = mock(FlowFormInstanceMapper.class);
        FlowProjectionHandler handler = new FlowProjectionHandler(
                taskMapper, candidateMapper, businessMapper, formMapper);
        FlowTask task = new FlowTask();
        task.setTenantId(7L);
        task.setTaskId("task-1");
        task.setProcessInstanceId("process-1");
        task.setTitle("审批任务");
        task.setCandidateUsers("11, 12");
        task.setCandidateGroups("role-a");
        when(taskMapper.applyProjection(7L, "task-1", task, "event-1", 21L)).thenReturn(0);
        when(taskMapper.selectByTaskIdAndTenant("task-1", 7L)).thenReturn(null);

        handler.apply(outbox(21L, "event-1"), new FlowProjectionOutboxPayload(
                FlowProjectionType.TASK_CREATED, task, null, null));

        verify(taskMapper).insert(task);
        assertEquals(21L, task.getProjectionSequence());
        ArgumentCaptor<FlowTaskCandidate> candidates = ArgumentCaptor.forClass(FlowTaskCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.times(3))
                .upsertProjection(candidates.capture(), eq("event-1"), eq(21L));
        assertEquals(3, candidates.getAllValues().size());
    }

    @Test
    void staleProcessProjectionCannotOverwriteNewerBusinessState() {
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowTaskCandidateMapper candidateMapper = mock(FlowTaskCandidateMapper.class);
        FlowBusinessMapper businessMapper = mock(FlowBusinessMapper.class);
        FlowFormInstanceMapper formMapper = mock(FlowFormInstanceMapper.class);
        FlowProjectionHandler handler = new FlowProjectionHandler(
                taskMapper, candidateMapper, businessMapper, formMapper);
        FlowBusiness snapshot = new FlowBusiness();
        snapshot.setTenantId(7L);
        snapshot.setProcessInstanceId("process-1");
        snapshot.setStatus("approved");
        FlowBusiness current = new FlowBusiness();
        current.setProjectionSequence(30L);
        when(businessMapper.applyProjection(eq(7L), eq("process-1"), eq("approved"),
                any(), any(), eq("event-1"), eq(21L))).thenReturn(0);
        when(businessMapper.selectByProcessInstanceIdAndTenantId("process-1", 7L)).thenReturn(current);

        handler.apply(outbox(21L, "event-1"), new FlowProjectionOutboxPayload(
                FlowProjectionType.PROCESS_COMPLETED, null, snapshot, "approved"));

        verify(businessMapper).selectByProcessInstanceIdAndTenantId("process-1", 7L);
        verify(taskMapper, never()).insert(any(FlowTask.class));
        verify(formMapper).applyProjectionStatus("process-1", "approved", 7L, "event-1", 21L);
    }

    private FlowProjectionOutbox outbox(long id, String eventId) {
        FlowProjectionOutbox outbox = new FlowProjectionOutbox();
        outbox.setId(id);
        outbox.setTenantId(7L);
        outbox.setEventId(eventId);
        return outbox;
    }
}
