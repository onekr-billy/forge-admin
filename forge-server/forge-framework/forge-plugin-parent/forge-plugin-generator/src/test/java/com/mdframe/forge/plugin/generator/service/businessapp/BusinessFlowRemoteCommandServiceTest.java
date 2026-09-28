package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowRemoteCommand;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowRemoteCommandStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowRemoteCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowRemoteCommandServiceTest {

    private BusinessFlowRemoteCommandMapper mapper;
    private BusinessFlowRemoteCommandService service;
    private FlowClient flowClient;
    private BusinessFlowRemoteStartRequest request;
    private BusinessFlowRemoteTaskRequest taskRequest;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessFlowRemoteCommandMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new BusinessFlowRemoteCommandService(mapper, transactionManager);
        flowClient = mock(FlowClient.class);

        request = new BusinessFlowRemoteStartRequest();
        request.setTenantId(1L);
        request.setObjectCode("purchase");
        request.setConfigKey("purchase_runtime");
        request.setRecordId(42L);
        request.setBusinessKey("purchase:42");
        request.setFlowBusinessKey("purchase:42");
        request.setFlowModelKey("purchase_approval");
        request.setTitle("采购审批");
        request.setVariables(Map.of("orderNo", "PO-42"));
        request.setStarterUserId(7L);
        request.setStarterUserName("张三");

        taskRequest = new BusinessFlowRemoteTaskRequest();
        taskRequest.setTenantId(1L);
        taskRequest.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT);
        taskRequest.setTaskId("task-42");
        taskRequest.setProcessInstanceId("process-42");
        taskRequest.setBusinessKey("purchase:42");
        taskRequest.setObjectCode("purchase");
        taskRequest.setRecordId(42L);
        taskRequest.setFlowModelKey("purchase_approval");
        taskRequest.setOperatorUserId(7L);
        taskRequest.setComment("修改后重提");
        taskRequest.setVariables(Map.of("amount", 100));
        taskRequest.setIdempotencyKey("flow:resubmit-42");
        taskRequest.setActionRequestDigest("a".repeat(64));
    }

    @Test
    void recoversKnownRemoteInstanceBeforeSendingDuplicateStart() {
        AiBusinessFlowRemoteCommand pending = command(BusinessFlowRemoteCommandStatus.PENDING, null, null);
        AiBusinessFlowRemoteCommand claimed = command(
                BusinessFlowRemoteCommandStatus.PROCESSING, null, "worker-1");
        AiBusinessFlowRemoteCommand succeeded = command(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, "process-42", null);
        when(mapper.claim(eq(1L), eq(10L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.selectByCommandId(1L, 10L)).thenReturn(claimed, succeeded);
        when(flowClient.getProcessStatus("purchase:42"))
                .thenReturn(FlowResult.success(Map.of("processInstanceId", "process-42")));
        when(mapper.markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class)))
                .thenReturn(1);

        AiBusinessFlowRemoteCommand result = service.executeStart(pending, flowClient);

        assertEquals("process-42", result.getProcessInstanceId());
        verify(flowClient, never()).startProcess(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reusesPersistedProcessIdWithoutCallingRemoteAgain() {
        AiBusinessFlowRemoteCommand retry = command(
                BusinessFlowRemoteCommandStatus.RETRY, "process-42", null);
        AiBusinessFlowRemoteCommand claimed = command(
                BusinessFlowRemoteCommandStatus.PROCESSING, "process-42", "worker-1");
        AiBusinessFlowRemoteCommand succeeded = command(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, "process-42", null);
        when(mapper.claim(eq(1L), eq(10L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.selectByCommandId(1L, 10L)).thenReturn(claimed, succeeded);
        when(mapper.markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class)))
                .thenReturn(1);

        AiBusinessFlowRemoteCommand result = service.executeStart(retry, flowClient);

        assertEquals("process-42", result.getProcessInstanceId());
        verify(flowClient, never()).getProcessStatus(anyString());
        verify(flowClient, never()).startProcess(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void persistsRemoteSuccessBeforeReturningToLocalFinalizer() {
        AiBusinessFlowRemoteCommand pending = command(BusinessFlowRemoteCommandStatus.PENDING, null, null);
        AiBusinessFlowRemoteCommand claimed = command(
                BusinessFlowRemoteCommandStatus.PROCESSING, null, "worker-1");
        AiBusinessFlowRemoteCommand succeeded = command(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, "process-42", null);
        when(mapper.claim(eq(1L), eq(10L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.selectByCommandId(1L, 10L)).thenReturn(claimed, succeeded);
        when(flowClient.getProcessStatus("purchase:42")).thenReturn(FlowResult.success(null));
        when(flowClient.startProcess(
                eq("purchase_approval"), eq("purchase:42"), eq("采购审批"), eq(request.getVariables()),
                eq("7"), eq("张三"), eq(null), eq(null)))
                .thenReturn(FlowResult.success("process-42"));
        when(mapper.markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class)))
                .thenReturn(1);

        AiBusinessFlowRemoteCommand result = service.executeStart(pending, flowClient);

        assertEquals(BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.getCode(), result.getCommandStatus());
        verify(mapper).markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class));
    }

    @Test
    void rejectsTamperedRecoveryPayload() {
        AiBusinessFlowRemoteCommand command = command(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, "process-42", null);
        command.setRequestDigest("0".repeat(64));

        assertThrows(RuntimeException.class, () -> service.restore(command));
    }

    @Test
    void replaysPersistedResubmitWithoutReadingCurrentTaskFirst() {
        AiBusinessFlowRemoteCommand pending = taskCommand(BusinessFlowRemoteCommandStatus.PENDING, null);
        AiBusinessFlowRemoteCommand claimed = taskCommand(
                BusinessFlowRemoteCommandStatus.PROCESSING, "worker-1");
        AiBusinessFlowRemoteCommand succeeded = taskCommand(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, null);
        when(mapper.claim(eq(1L), eq(20L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.selectByCommandId(1L, 20L)).thenReturn(claimed, succeeded);
        when(flowClient.approve(
                eq("task-42"), eq("7"), eq("修改后重提"), eq(null), eq(taskRequest.getVariables()),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64))))
                .thenReturn(FlowResult.success(null));
        when(mapper.markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class)))
                .thenReturn(1);

        AiBusinessFlowRemoteCommand result = service.executeTask(pending, flowClient);

        assertEquals(BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.getCode(), result.getCommandStatus());
        verify(flowClient).approve(
                eq("task-42"), eq("7"), eq("修改后重提"), eq(null), eq(taskRequest.getVariables()),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)));
    }

    @Test
    void rejectsTamperedTaskRecoveryPayload() {
        AiBusinessFlowRemoteCommand command = taskCommand(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, null);
        command.setBusinessKey("purchase:other");

        assertThrows(RuntimeException.class, () -> service.restoreTask(command));
    }

    @Test
    void replaysPersistedApprovalWithImmutableActionDetails() {
        taskRequest.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE);
        taskRequest.setSignature("signed");
        taskRequest.setApprovalPointResults(List.of(Map.of("code", "risk", "checked", true)));
        AiBusinessFlowRemoteCommand pending = prepareTaskExecution();
        when(flowClient.approve(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"), eq(taskRequest.getVariables()),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)),
                eq(taskRequest.getApprovalPointResults())))
                .thenReturn(FlowResult.success(null));

        service.executeTask(pending, flowClient);

        verify(flowClient).approve(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"), eq(taskRequest.getVariables()),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)),
                eq(taskRequest.getApprovalPointResults()));
    }

    @Test
    void replaysPersistedRejectAndRejectToStartCommands() {
        taskRequest.setSignature("signed");
        taskRequest.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_REJECT);
        AiBusinessFlowRemoteCommand reject = prepareTaskExecution();
        when(flowClient.reject(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64))))
                .thenReturn(FlowResult.success(null));

        service.executeTask(reject, flowClient);

        verify(flowClient).reject(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)));

        taskRequest.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_REJECT_TO_START);
        AiBusinessFlowRemoteCommand rejectToStart = prepareTaskExecution();
        when(flowClient.rejectToStart(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64))))
                .thenReturn(FlowResult.success(null));

        service.executeTask(rejectToStart, flowClient);

        verify(flowClient).rejectToStart(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)));
    }

    @Test
    void replaysPersistedReturnTarget() {
        taskRequest.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_RETURN);
        taskRequest.setSignature("signed");
        taskRequest.setTargetActivityId("review-node");
        AiBusinessFlowRemoteCommand pending = prepareTaskExecution();
        when(flowClient.returnTask(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"), eq("review-node"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64))))
                .thenReturn(FlowResult.success(null));

        service.executeTask(pending, flowClient);

        verify(flowClient).returnTask(
                eq("task-42"), eq("7"), eq("修改后重提"), eq("signed"), eq("review-node"),
                eq(1L), eq("flow:resubmit-42"), eq("a".repeat(64)));
    }

    private AiBusinessFlowRemoteCommand prepareTaskExecution() {
        AiBusinessFlowRemoteCommand pending = taskCommand(BusinessFlowRemoteCommandStatus.PENDING, null);
        AiBusinessFlowRemoteCommand claimed = taskCommand(
                BusinessFlowRemoteCommandStatus.PROCESSING, "worker-1");
        AiBusinessFlowRemoteCommand succeeded = taskCommand(
                BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED, null);
        when(mapper.claim(eq(1L), eq(20L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.selectByCommandId(1L, 20L)).thenReturn(claimed, succeeded);
        when(mapper.markRemoteSucceeded(eq(claimed), eq("process-42"), any(LocalDateTime.class)))
                .thenReturn(1);
        return pending;
    }

    private AiBusinessFlowRemoteCommand command(
            BusinessFlowRemoteCommandStatus status, String processInstanceId, String lockOwner) {
        AiBusinessFlowRemoteCommand command = new AiBusinessFlowRemoteCommand();
        command.setId(10L);
        command.setTenantId(1L);
        command.setCommandKey(BusinessFlowRemoteStartEnvelope.commandKey(request));
        command.setCommandType(BusinessFlowRemoteStartEnvelope.COMMAND_TYPE);
        command.setRequestDigest(BusinessFlowRemoteStartEnvelope.requestDigest(request));
        command.setRequestPayload(BusinessFlowRemoteStartEnvelope.requestPayload(request));
        command.setObjectCode(request.getObjectCode());
        command.setRecordId(request.getRecordId());
        command.setBusinessKey(request.getBusinessKey());
        command.setFlowBusinessKey(request.getFlowBusinessKey());
        command.setFlowModelKey(request.getFlowModelKey());
        command.setProcessInstanceId(processInstanceId);
        command.setCommandStatus(status.getCode());
        command.setRetryCount(BusinessFlowRemoteCommandStatus.PROCESSING == status ? 1 : 0);
        command.setLockOwner(lockOwner);
        return command;
    }

    private AiBusinessFlowRemoteCommand taskCommand(
            BusinessFlowRemoteCommandStatus status, String lockOwner) {
        AiBusinessFlowRemoteCommand command = new AiBusinessFlowRemoteCommand();
        command.setId(20L);
        command.setTenantId(1L);
        command.setCommandKey(BusinessFlowRemoteTaskEnvelope.commandKey(taskRequest));
        command.setCommandType(taskRequest.getCommandType());
        command.setRequestDigest(BusinessFlowRemoteTaskEnvelope.requestDigest(taskRequest));
        command.setRequestPayload(BusinessFlowRemoteTaskEnvelope.requestPayload(taskRequest));
        command.setObjectCode(taskRequest.getObjectCode());
        command.setRecordId(taskRequest.getRecordId());
        command.setBusinessKey(taskRequest.getBusinessKey());
        command.setFlowBusinessKey(taskRequest.getBusinessKey());
        command.setFlowModelKey(taskRequest.getFlowModelKey());
        command.setProcessInstanceId(taskRequest.getProcessInstanceId());
        command.setCommandStatus(status.getCode());
        command.setRetryCount(BusinessFlowRemoteCommandStatus.PROCESSING == status ? 1 : 0);
        command.setLockOwner(lockOwner);
        return command;
    }
}
