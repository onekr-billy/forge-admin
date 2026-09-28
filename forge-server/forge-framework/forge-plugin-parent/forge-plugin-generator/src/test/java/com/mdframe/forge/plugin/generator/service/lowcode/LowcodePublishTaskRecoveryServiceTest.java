package com.mdframe.forge.plugin.generator.service.lowcode;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.enums.LowcodePublishTaskStatus;
import com.mdframe.forge.plugin.generator.mapper.LowcodePublishTaskMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LowcodePublishTaskRecoveryServiceTest {

    private LowcodePublishTaskMapper mapper;
    private LowcodePublishTaskService taskService;
    private LowcodePublishTaskRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        mapper = mock(LowcodePublishTaskMapper.class);
        taskService = mock(LowcodePublishTaskService.class);
        recoveryService = new LowcodePublishTaskRecoveryService(mapper, taskService);
    }

    @Test
    void manualReplayValidatesCommandThenUsesTenantCasAndAuditIdentity() {
        AiLowcodePublishTask dead = task(LowcodePublishTaskStatus.DEAD);
        AiLowcodePublishTask replayed = task(LowcodePublishTaskStatus.PENDING);
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(dead, replayed);
        when(mapper.requeueDead(eq(7L), eq(30L), eq(42L),
                eq("已核对业务表结构"), any(LocalDateTime.class))).thenReturn(1);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            assertSame(replayed, recoveryService.requeueDead(
                    30L, 42L, " 已核对业务表结构 "));
        }

        verify(taskService).validateReplayable(dead);
        verify(mapper).requeueDead(eq(7L), eq(30L), eq(42L),
                eq("已核对业务表结构"), any(LocalDateTime.class));
    }

    @Test
    void crossTenantTaskIsHiddenBeforeReplayValidation() {
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(null);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            BusinessException failure = assertThrows(BusinessException.class,
                    () -> recoveryService.requeueDead(30L, 42L, "人工核对"));
            assertEquals("发布死信不存在或不属于当前租户", failure.getMessage());
        }

        verifyNoInteractions(taskService);
        verify(mapper, never()).requeueDead(any(), any(), any(), any(), any());
    }

    @Test
    void nonDeadTaskCannotBeRequeued() {
        AiLowcodePublishTask retrying = task(LowcodePublishTaskStatus.RETRY);
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(retrying);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            BusinessException failure = assertThrows(BusinessException.class,
                    () -> recoveryService.requeueDead(30L, 42L, "人工核对"));
            assertEquals("仅允许重放 DEAD 状态的发布任务", failure.getMessage());
        }

        verifyNoInteractions(taskService);
    }

    @Test
    void invalidImmutableCommandRemainsDead() {
        AiLowcodePublishTask dead = task(LowcodePublishTaskStatus.DEAD);
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(dead);
        doThrow(new BusinessException("命令摘要校验失败"))
                .when(taskService).validateReplayable(dead);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            assertThrows(BusinessException.class,
                    () -> recoveryService.requeueDead(30L, 42L, "人工核对"));
        }

        verify(mapper, never()).requeueDead(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentStatusChangeFailsAfterReplayValidation() {
        AiLowcodePublishTask dead = task(LowcodePublishTaskStatus.DEAD);
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(dead);
        when(mapper.requeueDead(eq(7L), eq(30L), eq(42L),
                eq("人工核对"), any(LocalDateTime.class))).thenReturn(0);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            BusinessException failure = assertThrows(BusinessException.class,
                    () -> recoveryService.requeueDead(30L, 42L, "人工核对"));
            assertEquals("发布死信状态已变化，请刷新后重试", failure.getMessage());
        }

        verify(taskService).validateReplayable(dead);
        verify(mapper).requeueDead(eq(7L), eq(30L), eq(42L),
                eq("人工核对"), any(LocalDateTime.class));
    }

    @Test
    void missingTrustedTenantStopsBeforeDataAccess() {
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> recoveryService.requeueDead(30L, 42L, "人工核对"));
        }

        verifyNoInteractions(mapper, taskService);
    }

    @Test
    void genericEntityUpdatesCannotOverwriteReplayAuditFields() throws Exception {
        for (String fieldName : new String[]{
                "replayCount", "replayedBy", "replayedTime", "replayReason"}) {
            TableField field = AiLowcodePublishTask.class.getDeclaredField(fieldName)
                    .getAnnotation(TableField.class);
            assertNotNull(field, fieldName);
            assertEquals(FieldStrategy.NEVER, field.insertStrategy(), fieldName);
            assertEquals(FieldStrategy.NEVER, field.updateStrategy(), fieldName);
        }
    }

    private AiLowcodePublishTask task(LowcodePublishTaskStatus status) {
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(30L);
        task.setTenantId(7L);
        task.setTaskStatus(status.getCode());
        return task;
    }
}
