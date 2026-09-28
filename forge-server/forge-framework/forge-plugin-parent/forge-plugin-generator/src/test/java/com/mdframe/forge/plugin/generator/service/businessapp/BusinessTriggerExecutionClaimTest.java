package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerLogMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("业务触发器事件认领")
class BusinessTriggerExecutionClaimTest {

    @Test
    @DisplayName("首次认领落库并分配日志ID")
    void claimsNewEvent() {
        BusinessTriggerLogMapper logMapper = mock(BusinessTriggerLogMapper.class);
        when(logMapper.insert(any(AiBusinessTriggerLog.class))).thenReturn(1);
        when(logMapper.claimExecution(anyLong(), anyLong(), any(), any(), any(), anyInt()))
                .thenReturn(1);
        when(logMapper.selectByLogId(anyLong(), anyLong())).thenAnswer(invocation -> claimedLog(
                invocation.getArgument(1)));
        BusinessTriggerService service = new BusinessTriggerService(mock(BusinessTriggerMapper.class), logMapper);
        AiBusinessTriggerLog log = logEntry();

        assertTrue(service.tryClaimExecution(log));
        assertNotNull(log.getId());
    }

    @Test
    @DisplayName("唯一键冲突按重复事件处理")
    void rejectsDuplicateEventClaim() {
        BusinessTriggerLogMapper logMapper = mock(BusinessTriggerLogMapper.class);
        when(logMapper.insert(any(AiBusinessTriggerLog.class)))
                .thenThrow(new DuplicateKeyException("duplicate event"));
        when(logMapper.selectByExecutionKey(1L, 10L, "BEV1:event")).thenReturn(logEntry());
        BusinessTriggerService service = new BusinessTriggerService(mock(BusinessTriggerMapper.class), logMapper);

        assertFalse(service.tryClaimExecution(logEntry()));
    }

    @Test
    @DisplayName("缺少稳定事件ID时拒绝认领")
    void rejectsMissingEventId() {
        BusinessTriggerService service = new BusinessTriggerService(
                mock(BusinessTriggerMapper.class), mock(BusinessTriggerLogMapper.class));
        AiBusinessTriggerLog log = logEntry();
        log.setEventId(null);

        assertThrows(BusinessException.class, () -> service.tryClaimExecution(log));
    }

    @Test
    @DisplayName("失败执行按退避时间进入可恢复状态")
    void schedulesFailedExecutionRetry() {
        BusinessTriggerLogMapper logMapper = mock(BusinessTriggerLogMapper.class);
        when(logMapper.updateExecutionResult(any())).thenReturn(1);
        BusinessTriggerService service = new BusinessTriggerService(mock(BusinessTriggerMapper.class), logMapper);
        AiBusinessTriggerLog log = claimedLog(99L);
        log.setExecuteStatus("FAILED");

        service.updateExecutionLog(log);

        assertEquals("FAILED", log.getExecuteStatus());
        assertNotNull(log.getNextRetryTime());
    }

    @Test
    @DisplayName("达到最大尝试次数后进入 DEAD 并停止自动重试")
    void deadLettersExhaustedExecution() {
        BusinessTriggerLogMapper logMapper = mock(BusinessTriggerLogMapper.class);
        when(logMapper.updateExecutionResult(any())).thenReturn(1);
        BusinessTriggerService service = new BusinessTriggerService(mock(BusinessTriggerMapper.class), logMapper);
        AiBusinessTriggerLog log = claimedLog(99L);
        log.setRetryCount(5);
        log.setExecuteStatus("FAILED");

        service.updateExecutionLog(log);

        assertEquals("DEAD", log.getExecuteStatus());
        assertNull(log.getNextRetryTime());
    }

    private AiBusinessTriggerLog logEntry() {
        AiBusinessTriggerLog log = new AiBusinessTriggerLog();
        log.setTenantId(1L);
        log.setTriggerId(10L);
        log.setEventId("BEV1:event");
        log.setEventDigest("digest");
        log.setTriggerSnapshot("{}");
        log.setExecutionDigest("execution-digest");
        return log;
    }

    private AiBusinessTriggerLog claimedLog(Long id) {
        AiBusinessTriggerLog log = logEntry();
        log.setId(id);
        log.setRetryCount(1);
        log.setLockOwner("worker");
        return log;
    }
}
