package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowTaskStatus;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建时直接指定处理人（assignee 非空）的任务应自动签收为 CLAIMED；
 * 候选人/候选组模式保持 PENDING，与修复前行为一致。
 * 对应 spec：flow-auto-claim-created-task 功能点 F1。
 */
@DisplayName("flow task created auto-claim for directly assigned tasks")
class FlowTaskEventListenerCreatedAutoClaimTest {

    private FlowTaskEventListener listener;
    private FlowTaskMapper flowTaskMapper;
    private TaskService taskService;

    @BeforeEach
    void setUp() throws Exception {
        listener = new FlowTaskEventListener();
        flowTaskMapper = mock(FlowTaskMapper.class);
        FlowBusinessMapper flowBusinessMapper = mock(FlowBusinessMapper.class);
        taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        HistoryService historyService = mock(HistoryService.class);

        inject("flowTaskMapper", flowTaskMapper);
        inject("flowBusinessMapper", flowBusinessMapper);
        inject("taskService", taskService);
        inject("runtimeService", runtimeService);
        inject("historyService", historyService);
        inject("eventPublisher", mock(ApplicationEventPublisher.class));
        // 无 assignee 且无候选人的边界用例会触发「审批人分配失败」错误日志分支，需注入避免 NPE
        inject("flowErrorLogService", mock(FlowErrorLogService.class));

        when(flowTaskMapper.selectByTaskId(anyString())).thenReturn(null);
        when(flowBusinessMapper.selectByProcessInstanceId(anyString())).thenReturn(business());
        when(taskService.getIdentityLinksForTask(anyString())).thenReturn(List.of());
        HistoricVariableInstanceQuery historicQuery = mock(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.processInstanceId(anyString())).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of());
    }

    @Test
    @DisplayName("assignee present without owner is created as CLAIMED with claim time")
    void shouldCreateClaimedTaskWhenAssigneePresent() {
        FlowTask inserted = dispatchCreated("1", null, List.of());

        assertEquals(FlowTaskStatus.CLAIMED.getCode(), inserted.getStatus());
        assertNotNull(inserted.getClaimTime());
    }

    @Test
    @DisplayName("owner equal to assignee keeps created task CLAIMED")
    void shouldCreateClaimedTaskWhenOwnerEqualsAssignee() {
        FlowTask inserted = dispatchCreated("1", "1", List.of());

        assertEquals(FlowTaskStatus.CLAIMED.getCode(), inserted.getStatus());
        assertNotNull(inserted.getClaimTime());
    }

    @Test
    @DisplayName("owner different from assignee keeps created task PENDING")
    void shouldKeepPendingWhenOwnerDiffersFromAssignee() {
        FlowTask inserted = dispatchCreated("1", "2", List.of());

        assertEquals(FlowTaskStatus.PENDING.getCode(), inserted.getStatus());
        assertNull(inserted.getClaimTime());
    }

    @Test
    @DisplayName("candidate-mode task without assignee stays PENDING")
    void shouldKeepPendingWhenNoAssignee() {
        FlowTask inserted = dispatchCreated(null, null, List.of());

        assertEquals(FlowTaskStatus.PENDING.getCode(), inserted.getStatus());
        assertNull(inserted.getClaimTime());
    }

    @Test
    @DisplayName("candidate-mode task with candidate users still stays PENDING")
    void shouldKeepPendingWhenCandidateUsersPresent() {
        IdentityLink candidate = mock(IdentityLink.class);
        when(candidate.getType()).thenReturn("candidate");
        when(candidate.getUserId()).thenReturn("2");

        FlowTask inserted = dispatchCreated(null, null, List.of(candidate));

        assertEquals(FlowTaskStatus.PENDING.getCode(), inserted.getStatus());
        assertNull(inserted.getClaimTime());
    }

    private FlowTask dispatchCreated(String assignee, String owner, List<IdentityLink> identityLinks) {
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn("task-auto-claim-001");
        when(task.getName()).thenReturn("审批人");
        when(task.getTaskDefinitionKey()).thenReturn("Node_1");
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getProcessDefinitionId()).thenReturn("leave:1:definition-001");
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getOwner()).thenReturn(owner);
        when(taskService.getIdentityLinksForTask("task-auto-claim-001")).thenReturn(identityLinks);

        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);

        listener.onEvent(event);

        ArgumentCaptor<FlowTask> captor = ArgumentCaptor.forClass(FlowTask.class);
        verify(flowTaskMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static FlowBusiness business() {
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        business.setTitle("测试审批流程");
        business.setBusinessKey("FLOW_TEST:auto-claim:1");
        business.setBusinessType("FLOW_MODEL_TEST");
        business.setApplyUserId("1");
        business.setApplyUserName("超级管理员");
        return business;
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = FlowTaskEventListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(listener, value);
    }
}
