package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BusinessFlowRemoteTaskEnvelopeTest {

    @Test
    void canonicalPayloadAndCommandIdentityAreStable() {
        BusinessFlowRemoteTaskRequest first = request(new LinkedHashMap<>(Map.of("b", 2, "a", 1)));
        BusinessFlowRemoteTaskRequest reordered = request(new LinkedHashMap<>(Map.of("a", 1, "b", 2)));

        assertEquals(BusinessFlowRemoteTaskEnvelope.commandKey(first),
                BusinessFlowRemoteTaskEnvelope.commandKey(reordered));
        assertEquals(BusinessFlowRemoteTaskEnvelope.requestDigest(first),
                BusinessFlowRemoteTaskEnvelope.requestDigest(reordered));

        reordered.setComment("另一份修改说明");
        assertNotEquals(BusinessFlowRemoteTaskEnvelope.requestDigest(first),
                BusinessFlowRemoteTaskEnvelope.requestDigest(reordered));
    }

    @Test
    void restoredSnapshotPreservesRecoveryIdentity() {
        BusinessFlowRemoteTaskRequest source = request(Map.of("amount", 100));

        BusinessFlowRemoteTaskRequest restored = BusinessFlowRemoteTaskEnvelope.restore(
                BusinessFlowRemoteTaskEnvelope.requestPayload(source));

        assertEquals(source.getTenantId(), restored.getTenantId());
        assertEquals(source.getTaskId(), restored.getTaskId());
        assertEquals(source.getProcessInstanceId(), restored.getProcessInstanceId());
        assertEquals(source.getActionRequestDigest(), restored.getActionRequestDigest());
        assertEquals(BusinessFlowRemoteTaskEnvelope.requestDigest(source),
                BusinessFlowRemoteTaskEnvelope.requestDigest(restored));
    }

    private BusinessFlowRemoteTaskRequest request(Map<String, Object> variables) {
        BusinessFlowRemoteTaskRequest request = new BusinessFlowRemoteTaskRequest();
        request.setTenantId(1L);
        request.setCommandType(BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT);
        request.setTaskId("task-42");
        request.setProcessInstanceId("process-42");
        request.setBusinessKey("purchase:42");
        request.setObjectCode("purchase");
        request.setRecordId(42L);
        request.setFlowModelKey("purchase_approval");
        request.setOperatorUserId(7L);
        request.setComment("修改后重提");
        request.setVariables(variables);
        request.setIdempotencyKey("flow:resubmit-42");
        request.setActionRequestDigest("a".repeat(64));
        return request;
    }
}
