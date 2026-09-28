package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowStatusSyncStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessFlowStatusReconciliationServiceTest {

    private BusinessFlowInstanceLinkMapper mapper;
    private BusinessFlowStatusReconciliationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessFlowInstanceLinkMapper.class);
        service = new BusinessFlowStatusReconciliationService(mapper);
        ReflectionTestUtils.setField(service, "maxRetryCount", 3);
        ReflectionTestUtils.setField(service, "lockTimeoutSeconds", 120L);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 5L);
        ReflectionTestUtils.setField(service, "runningIntervalSeconds", 60L);
    }

    @Test
    void claimUsesTenantScopedCompareAndSetAndReturnsLeaseOwner() {
        AiBusinessFlowInstanceLink candidate = link(BusinessFlowStatusSyncStatus.PENDING, 0);
        AiBusinessFlowInstanceLink claimed = link(BusinessFlowStatusSyncStatus.PROCESSING, 1);
        claimed.setStatusSyncLockOwner("worker-1");
        when(mapper.claimStatusSync(eq(7L), eq(10L), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(3))).thenReturn(1);
        when(mapper.selectByLinkId(7L, 10L)).thenReturn(claimed);

        AiBusinessFlowInstanceLink result = service.claim(candidate);

        assertSame(claimed, result);
        assertEquals("worker-1", result.getStatusSyncLockOwner());
    }

    @Test
    void successfulRunningReadResetsRetryAndSchedulesNextPoll() {
        AiBusinessFlowInstanceLink claimed = link(BusinessFlowStatusSyncStatus.PROCESSING, 2);
        claimed.setStatusSyncLockOwner("worker-1");
        when(mapper.markStatusSyncWaiting(eq(claimed), eq("running"),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        service.markRunning(claimed, "running");

        verify(mapper).markStatusSyncWaiting(eq(claimed), eq("running"),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void exhaustedLeaseMovesToDeadWithoutLeakingFailureMessage() {
        AiBusinessFlowInstanceLink claimed = link(BusinessFlowStatusSyncStatus.PROCESSING, 3);
        claimed.setStatusSyncLockOwner("worker-1");
        when(mapper.markStatusSyncFailed(eq(claimed), anyString(), any(), anyString(),
                any(LocalDateTime.class))).thenReturn(1);

        service.markFailed(claimed, new IllegalStateException("sensitive remote payload"));

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorType = ArgumentCaptor.forClass(String.class);
        verify(mapper).markStatusSyncFailed(eq(claimed), status.capture(), eq(null), errorType.capture(),
                any(LocalDateTime.class));
        assertEquals(BusinessFlowStatusSyncStatus.DEAD.getCode(), status.getValue());
        assertEquals("IllegalStateException", errorType.getValue());
    }

    @Test
    void manualReplayUsesTenantCasAndPersistsAuditIdentity() {
        AiBusinessFlowInstanceLink replayed = link(BusinessFlowStatusSyncStatus.PENDING, 0);
        when(mapper.requeueDeadStatusSync(eq(7L), eq(10L), eq(42L),
                eq("已核对远端流程仍可查询"), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.selectByLinkId(7L, 10L)).thenReturn(replayed);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            assertSame(replayed, service.requeueDead(
                    10L, 42L, " 已核对远端流程仍可查询 "));
        }

        verify(mapper).requeueDeadStatusSync(eq(7L), eq(10L), eq(42L),
                eq("已核对远端流程仍可查询"), any(LocalDateTime.class));
    }

    @Test
    void manualReplayDoesNotRevealCrossTenantRecord() {
        when(mapper.requeueDeadStatusSync(eq(7L), eq(99L), eq(42L),
                eq("人工核对"), any(LocalDateTime.class))).thenReturn(0);
        when(mapper.selectByLinkId(7L, 99L)).thenReturn(null);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            BusinessException failure = assertThrows(BusinessException.class,
                    () -> service.requeueDead(99L, 42L, "人工核对"));
            assertEquals("流程状态对账死信不存在或不属于当前租户", failure.getMessage());
        }
    }

    @Test
    void manualReplayRejectsNonDeadOrAlreadyTerminalRecord() {
        AiBusinessFlowInstanceLink existing = link(BusinessFlowStatusSyncStatus.RETRY, 1);
        when(mapper.requeueDeadStatusSync(eq(7L), eq(10L), eq(42L),
                eq("人工核对"), any(LocalDateTime.class))).thenReturn(0);
        when(mapper.selectByLinkId(7L, 10L)).thenReturn(existing);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            BusinessException failure = assertThrows(BusinessException.class,
                    () -> service.requeueDead(10L, 42L, "人工核对"));
            assertEquals("仅允许重放仍处于非终态的流程状态对账死信", failure.getMessage());
        }
    }

    @Test
    void manualReplayRejectsMissingTrustedTenantBeforeDataAccess() {
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.requeueDead(10L, 42L, "人工核对"));
        }

        verifyNoInteractions(mapper);
    }

    @Test
    void genericEntityUpdatesCannotOverwriteLeaseFencingColumns() throws Exception {
        for (String fieldName : new String[]{
                "statusSyncStatus", "statusSyncRetryCount", "statusSyncNextTime",
                "statusSyncLockOwner", "statusSyncLockTime", "statusSyncRemoteStatus",
                "statusSyncErrorType", "statusSyncedTime", "statusSyncReplayCount",
                "statusSyncReplayedBy", "statusSyncReplayedTime", "statusSyncReplayReason"}) {
            TableField field = AiBusinessFlowInstanceLink.class.getDeclaredField(fieldName)
                    .getAnnotation(TableField.class);
            assertNotNull(field, fieldName);
            assertEquals(FieldStrategy.NEVER, field.insertStrategy(), fieldName);
            assertEquals(FieldStrategy.NEVER, field.updateStrategy(), fieldName);
        }
    }

    private AiBusinessFlowInstanceLink link(BusinessFlowStatusSyncStatus status, int retryCount) {
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setId(10L);
        link.setTenantId(7L);
        link.setStatusSyncStatus(status.getCode());
        link.setStatusSyncRetryCount(retryCount);
        return link;
    }
}
