package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityBalance;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityLedger;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityOperationDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityBalanceMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLedgerMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLockMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityOperationResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessQuantityLedgerService")
class BusinessQuantityLedgerServiceTest {

    private final BusinessQuantityBalanceMapper balanceMapper = Mockito.mock(BusinessQuantityBalanceMapper.class);
    private final BusinessQuantityLedgerMapper ledgerMapper = Mockito.mock(BusinessQuantityLedgerMapper.class);
    private final BusinessQuantityLockMapper lockMapper = Mockito.mock(BusinessQuantityLockMapper.class);
    private final BusinessQuantityLedgerService service = new BusinessQuantityLedgerService(
            new ObjectMapper(), balanceMapper, ledgerMapper, lockMapper);

    @Test
    @DisplayName("inbound returns existing result when idempotency key was already used")
    void inboundIdempotentHitDoesNotWriteBalanceAgain() {
        AiBusinessQuantityLedger ledger = new AiBusinessQuantityLedger();
        ledger.setId(10L);
        ledger.setOperationType("INBOUND");
        ledger.setAccountCode("main");
        ledger.setItemCode("sku-1");
        ledger.setDimensionKey("");
        ledger.setQuantityDelta(10L);
        ledger.setBalanceQuantity(10L);
        ledger.setLockedQuantity(0L);
        when(ledgerMapper.selectByIdempotencyKey(1L, "K1")).thenReturn(ledger);

        BusinessQuantityOperationDTO dto = new BusinessQuantityOperationDTO();
        dto.setOperationType("INBOUND");
        dto.setAccountCode("main");
        dto.setItemCode("sku-1");
        dto.setQuantity(10L);
        dto.setIdempotencyKey("K1");

        BusinessQuantityOperationResultVO result = service.inbound(dto);

        assertTrue(result.getIdempotentHit());
        assertEquals(10L, result.getLedgerId());
        assertEquals(10L, result.getBalanceQuantity());
        verify(balanceMapper, never()).increaseQuantity(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("operation rejects when idempotency key is missing")
    void rejectsMissingIdempotencyKey() {
        BusinessQuantityOperationDTO dto = new BusinessQuantityOperationDTO();
        dto.setOperationType("INBOUND");
        dto.setAccountCode("main");
        dto.setItemCode("sku-1");
        dto.setQuantity(10L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.inbound(dto));

        assertEquals("数量台账幂等键不能为空", error.getMessage());
        verify(ledgerMapper, never()).insert(any(AiBusinessQuantityLedger.class));
        verify(balanceMapper, never()).increaseQuantity(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("lock rejects when available quantity is not enough")
    void lockRejectsWhenAvailableQuantityNotEnough() {
        when(balanceMapper.selectByKey(1L, "main", "sku-1", "")).thenReturn(balance("main", "sku-1", "", 5L, 0L));
        when(balanceMapper.lockQuantity(1L, "main", "sku-1", "", 6L)).thenReturn(0);

        BusinessQuantityOperationDTO dto = new BusinessQuantityOperationDTO();
        dto.setOperationType("LOCK");
        dto.setAccountCode("main");
        dto.setItemCode("sku-1");
        dto.setQuantity(6L);
        dto.setIdempotencyKey("L1");

        BusinessException error = assertThrows(BusinessException.class, () -> service.lock(dto));

        assertEquals("可用数量不足，锁定失败", error.getMessage());
        verify(lockMapper, never()).insert(any(com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityLock.class));
    }

    @Test
    @DisplayName("transfer decreases source and increases target in one operation")
    void transferUpdatesSourceAndTarget() {
        when(balanceMapper.selectByKey(eq(1L), eq("source"), eq("sku-1"), eq("")))
                .thenReturn(balance("source", "sku-1", "", 10L, 0L),
                        balance("source", "sku-1", "", 7L, 0L));
        when(balanceMapper.selectByKey(eq(1L), eq("target"), eq("sku-1"), eq("")))
                .thenReturn(balance("target", "sku-1", "", 0L, 0L),
                        balance("target", "sku-1", "", 3L, 0L));
        when(balanceMapper.decreaseAvailableQuantity(1L, "source", "sku-1", "", 3L)).thenReturn(1);
        when(balanceMapper.increaseQuantity(1L, "target", "sku-1", "", 3L)).thenReturn(1);
        AtomicLong ledgerId = new AtomicLong(20L);
        Mockito.doAnswer(invocation -> {
            AiBusinessQuantityLedger ledger = invocation.getArgument(0);
            ledger.setId(ledgerId.getAndIncrement());
            return 1;
        }).when(ledgerMapper).insert(any(AiBusinessQuantityLedger.class));

        BusinessQuantityOperationDTO dto = new BusinessQuantityOperationDTO();
        dto.setOperationType("TRANSFER");
        dto.setAccountCode("source");
        dto.setTargetAccountCode("target");
        dto.setItemCode("sku-1");
        dto.setQuantity(3L);
        dto.setIdempotencyKey("T1");

        BusinessQuantityOperationResultVO result = service.transfer(dto);

        assertEquals(20L, result.getLedgerId());
        assertEquals(21L, result.getTargetLedgerId());
        assertEquals(7L, result.getBalanceQuantity());
        assertEquals(3L, result.getTargetBalanceQuantity());
        verify(balanceMapper).decreaseAvailableQuantity(1L, "source", "sku-1", "", 3L);
        verify(balanceMapper).increaseQuantity(1L, "target", "sku-1", "", 3L);
        ArgumentCaptor<AiBusinessQuantityLedger> ledgerCaptor = ArgumentCaptor.forClass(AiBusinessQuantityLedger.class);
        verify(ledgerMapper, times(2)).insert(ledgerCaptor.capture());
        List<AiBusinessQuantityLedger> ledgers = ledgerCaptor.getAllValues();
        assertEquals("T1:source", ledgers.get(0).getIdempotencyKey());
        assertEquals(-3L, ledgers.get(0).getQuantityDelta());
        assertEquals("source", ledgers.get(0).getAccountCode());
        assertEquals("T1:target", ledgers.get(1).getIdempotencyKey());
        assertEquals(3L, ledgers.get(1).getQuantityDelta());
        assertEquals("target", ledgers.get(1).getAccountCode());
    }

    private AiBusinessQuantityBalance balance(String accountCode,
                                              String itemCode,
                                              String dimensionKey,
                                              Long quantity,
                                              Long lockedQuantity) {
        AiBusinessQuantityBalance balance = new AiBusinessQuantityBalance();
        balance.setTenantId(1L);
        balance.setAccountCode(accountCode);
        balance.setItemCode(itemCode);
        balance.setDimensionKey(dimensionKey);
        balance.setQuantity(quantity);
        balance.setLockedQuantity(lockedQuantity);
        balance.setStatus(1);
        return balance;
    }
}
