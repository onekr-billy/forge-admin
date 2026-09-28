package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessFlowRemoteTaskRecoveryPolicyTest {

    @Test
    void acceptsOnlyOriginalOperatorAndExactActionIdentity() {
        BusinessFlowRemoteTaskRequest request = request();
        BusinessFlowCommandIdentity.Credentials credentials =
                new BusinessFlowCommandIdentity.Credentials("flow:approve-42", "a".repeat(64));

        assertDoesNotThrow(() -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request, 7L, BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE, "task-42", credentials,
                "submission-42"));
        assertThrows(BusinessException.class, () -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request, 8L, BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE, "task-42", credentials,
                "submission-42"));
        assertThrows(BusinessException.class, () -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request, 7L, BusinessFlowRemoteTaskEnvelope.COMMAND_RETURN, "task-42", credentials,
                "submission-42"));
        assertThrows(BusinessException.class, () -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request, 7L, BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE, "task-other", credentials,
                "submission-42"));
        assertThrows(BusinessException.class, () -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request, 7L, BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE, "task-42", credentials,
                "submission-other"));
    }

    @Test
    void allowsBackgroundDispatcherToUseValidatedPersistentSnapshot() {
        assertDoesNotThrow(() -> BusinessFlowRemoteTaskRecoveryPolicy.validateInteractive(
                request(), null, null, null, null, null));
    }

    private BusinessFlowRemoteTaskRequest request() {
        BusinessFlowRemoteTaskRequest request = new BusinessFlowRemoteTaskRequest();
        request.setOperatorUserId(7L);
        request.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE);
        request.setTaskId("task-42");
        request.setIdempotencyKey("flow:approve-42");
        request.setActionRequestDigest("a".repeat(64));
        request.setSubmissionDigest("submission-42");
        return request;
    }
}
