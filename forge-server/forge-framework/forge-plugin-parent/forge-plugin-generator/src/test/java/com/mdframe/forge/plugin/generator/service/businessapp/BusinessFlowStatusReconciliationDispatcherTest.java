package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowStatusSyncStatus;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowStatusReconciliationDispatcherTest {

    private BusinessFlowStatusReconciliationService reconciliationService;
    private FlowClient flowClient;
    private BusinessFlowService businessFlowService;
    private BusinessFlowStatusReconciliationDispatcher dispatcher;
    private AiBusinessFlowInstanceLink claimed;

    @BeforeEach
    void setUp() {
        reconciliationService = mock(BusinessFlowStatusReconciliationService.class);
        flowClient = mock(FlowClient.class);
        businessFlowService = mock(BusinessFlowService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FlowClient> flowClientProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BusinessFlowService> businessFlowServiceProvider = mock(ObjectProvider.class);
        dispatcher = new BusinessFlowStatusReconciliationDispatcher(
                reconciliationService, flowClientProvider, businessFlowServiceProvider);

        claimed = new AiBusinessFlowInstanceLink();
        claimed.setId(10L);
        claimed.setTenantId(7L);
        claimed.setObjectCode("purchase");
        claimed.setRecordId(42L);
        claimed.setBusinessKey("purchase:42");
        claimed.setProcessInstanceId("process-42");
        claimed.setStartUserId(9L);
        claimed.setVariablesSnapshot("{\"flowBusinessKey\":\"purchase:42:round:2\"}");
        claimed.setStatusSyncStatus(BusinessFlowStatusSyncStatus.PROCESSING.getCode());
        claimed.setStatusSyncLockOwner("worker-1");
        claimed.setStatusSyncRetryCount(1);
        when(reconciliationService.claim(claimed)).thenReturn(claimed);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void appliesApprovedRemoteStateThroughExistingTerminalCallback() {
        when(flowClient.getProcessStatus("purchase:42:round:2")).thenReturn(FlowResult.success(Map.of(
                "tenantId", 7L,
                "businessKey", "purchase:42:round:2",
                "processInstanceId", "process-42",
                "status", "approved")));
        when(flowClient.getProcessVariables("purchase:42:round:2"))
                .thenReturn(FlowResult.success(Map.of("approvalResult", "approve")));

        dispatcher.reconcileCandidate(claimed, flowClient, businessFlowService);

        ArgumentCaptor<BusinessFlowCallbackDTO> callbackCaptor =
                ArgumentCaptor.forClass(BusinessFlowCallbackDTO.class);
        verify(businessFlowService).handleFlowCallback(callbackCaptor.capture());
        BusinessFlowCallbackDTO callback = callbackCaptor.getValue();
        assertEquals(7L, callback.getTenantId());
        assertEquals("APPROVED", callback.getResult());
        assertEquals("approve", callback.getVariables().get("approvalResult"));
        verify(reconciliationService).markCompleted(claimed, "approved");
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void leavesRunningRemoteStateForNextBoundedPoll() {
        when(flowClient.getProcessStatus("purchase:42:round:2")).thenReturn(FlowResult.success(Map.of(
                "tenantId", 7L,
                "businessKey", "purchase:42:round:2",
                "processInstanceId", "process-42",
                "status", "running")));

        dispatcher.reconcileCandidate(claimed, flowClient, businessFlowService);

        verify(reconciliationService).markRunning(claimed, "running");
        verify(flowClient, never()).getProcessVariables(any());
        verify(businessFlowService, never()).handleFlowCallback(any());
    }

    @ParameterizedTest
    @CsvSource({
            "completed,APPROVED",
            "rejected,REJECTED",
            "canceled,CANCELED",
            "terminated,CANCELED"
    })
    void mapsEveryRemoteTerminalStateToDocumentTerminalState(
            String remoteStatus, String expectedResult) {
        when(flowClient.getProcessStatus("purchase:42:round:2")).thenReturn(FlowResult.success(Map.of(
                "tenantId", 7L,
                "businessKey", "purchase:42:round:2",
                "processInstanceId", "process-42",
                "status", remoteStatus)));
        when(flowClient.getProcessVariables("purchase:42:round:2"))
                .thenReturn(FlowResult.success(Map.of()));

        dispatcher.reconcileCandidate(claimed, flowClient, businessFlowService);

        ArgumentCaptor<BusinessFlowCallbackDTO> callbackCaptor =
                ArgumentCaptor.forClass(BusinessFlowCallbackDTO.class);
        verify(businessFlowService).handleFlowCallback(callbackCaptor.capture());
        assertEquals(expectedResult, callbackCaptor.getValue().getResult());
        verify(reconciliationService).markCompleted(claimed, remoteStatus);
    }

    @Test
    void rejectsCrossTenantRemoteIdentityAndRecordsRetry() {
        when(flowClient.getProcessStatus("purchase:42:round:2")).thenReturn(FlowResult.success(Map.of(
                "tenantId", 8L,
                "businessKey", "purchase:42:round:2",
                "processInstanceId", "process-42",
                "status", "approved")));

        dispatcher.reconcileCandidate(claimed, flowClient, businessFlowService);

        verify(reconciliationService).markFailed(any(AiBusinessFlowInstanceLink.class), any(RuntimeException.class));
        verify(businessFlowService, never()).handleFlowCallback(any());
        verify(reconciliationService, never()).markCompleted(any(), any());
    }
}
