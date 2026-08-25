package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("BusinessProcessApprovalResultListener")
class BusinessProcessApprovalResultListenerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("approval result resumes after commit in isolated transactions")
    void usesAfterCommitAndIsolatedTransactionBoundaries() throws Exception {
        Method listenerMethod = BusinessProcessApprovalResultListener.class.getMethod(
                "onApprovalResult", BusinessProcessApprovalResultEvent.class);
        TransactionalEventListener eventListener = listenerMethod.getAnnotation(TransactionalEventListener.class);
        assertNotNull(eventListener);
        assertEquals(TransactionPhase.AFTER_COMMIT, eventListener.phase());
        assertTrue(eventListener.fallbackExecution());

        Method flowCallbackMethod = BusinessFlowService.class.getMethod(
                "handleFlowEngineEvent", FlowEventContext.class);
        Transactional flowCallbackTransaction = flowCallbackMethod.getAnnotation(Transactional.class);
        assertNotNull(flowCallbackTransaction);

        Method resumeMethod = BusinessProcessOrchestrator.class.getMethod(
                "resumeApprovalResult", Long.class, String.class, String.class);
        Transactional resumeTransaction = resumeMethod.getAnnotation(Transactional.class);
        assertNotNull(resumeTransaction);
        assertEquals(Propagation.REQUIRES_NEW, resumeTransaction.propagation());

        Method actionMethod = BusinessProcessActionExecutor.class.getMethod(
                "execute",
                com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun.class,
                com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema.class,
                com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode.class);
        Transactional actionTransaction = actionMethod.getAnnotation(Transactional.class);
        assertNotNull(actionTransaction);
        assertEquals(Propagation.REQUIRES_NEW, actionTransaction.propagation());
    }

    @Test
    @DisplayName("resume failures are logged without rolling back the completed approval callback")
    void isolatesResumeFailureFromCompletedApprovalCallback() {
        BusinessProcessOrchestrator orchestrator = mock(BusinessProcessOrchestrator.class);
        BusinessProcessApprovalResultListener listener = new BusinessProcessApprovalResultListener(orchestrator);
        TenantContextHolder.setTenantId(99L);
        doAnswer(invocation -> {
            assertEquals(1L, TenantContextHolder.getTenantId());
            throw new IllegalStateException("action failed");
        }).when(orchestrator).resumeApprovalResult(1L, "flow-instance-1", "APPROVED");

        assertDoesNotThrow(() -> listener.onApprovalResult(
                new BusinessProcessApprovalResultEvent(1L, "flow-instance-1", "APPROVED")));

        verify(orchestrator).resumeApprovalResult(1L, "flow-instance-1", "APPROVED");
        assertEquals(99L, TenantContextHolder.getTenantId());
    }
}
