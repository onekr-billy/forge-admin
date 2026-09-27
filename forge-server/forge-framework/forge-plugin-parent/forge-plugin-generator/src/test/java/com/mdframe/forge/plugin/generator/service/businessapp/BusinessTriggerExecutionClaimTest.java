package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerLogMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("业务触发器事件认领")
class BusinessTriggerExecutionClaimTest {

    @Test
    @DisplayName("首次认领落库并分配日志ID")
    void claimsNewEvent() {
        BusinessTriggerLogMapper logMapper = mock(BusinessTriggerLogMapper.class);
        when(logMapper.insert(any(AiBusinessTriggerLog.class))).thenReturn(1);
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

    private AiBusinessTriggerLog logEntry() {
        AiBusinessTriggerLog log = new AiBusinessTriggerLog();
        log.setTenantId(1L);
        log.setTriggerId(10L);
        log.setEventId("BEV1:event");
        return log;
    }
}
