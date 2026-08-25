package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityBalance;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityLedger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessQuantityLock;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityOperationDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityBalanceMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLedgerMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLockMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityOperationResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 通用数量台账服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessQuantityLedgerService {

    private static final String OP_INBOUND = "INBOUND";
    private static final String OP_LOCK = "LOCK";
    private static final String OP_RELEASE = "RELEASE";
    private static final String OP_COMMIT = "COMMIT";
    private static final String OP_TRANSFER = "TRANSFER";
    private static final String TRANSFER_SOURCE_SUFFIX = ":source";
    private static final String TRANSFER_TARGET_SUFFIX = ":target";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final String STATUS_COMMITTED = "COMMITTED";

    private final ObjectMapper objectMapper;
    private final BusinessQuantityBalanceMapper balanceMapper;
    private final BusinessQuantityLedgerMapper ledgerMapper;
    private final BusinessQuantityLockMapper lockMapper;

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO operate(BusinessQuantityOperationDTO dto) {
        String operationType = normalizeOperationType(dto == null ? null : dto.getOperationType());
        return switch (operationType) {
            case OP_INBOUND -> inbound(dto);
            case OP_LOCK -> lock(dto);
            case OP_RELEASE -> release(dto);
            case OP_COMMIT -> commit(dto);
            case OP_TRANSFER -> transfer(dto);
            default -> throw new BusinessException("不支持的数量台账操作: " + operationType);
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO inbound(BusinessQuantityOperationDTO dto) {
        Long tenantId = resolveTenantId();
        normalizeCommon(dto, OP_INBOUND);
        BusinessQuantityOperationResultVO idempotent = idempotentResult(tenantId, dto);
        if (idempotent != null) {
            return idempotent;
        }

        ensureBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        int updated = balanceMapper.increaseQuantity(tenantId, dto.getAccountCode(), dto.getItemCode(),
                dto.getDimensionKey(), dto.getQuantity());
        if (updated <= 0) {
            throw new BusinessException("数量入账失败，数量账户不可用");
        }
        AiBusinessQuantityBalance balance = currentBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        AiBusinessQuantityLedger ledger = insertLedger(tenantId, dto, OP_INBOUND, dto.getQuantity(), balance, null);
        return buildResult(dto, ledger, balance, null, false, "数量已入账");
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO lock(BusinessQuantityOperationDTO dto) {
        Long tenantId = resolveTenantId();
        normalizeCommon(dto, OP_LOCK);
        BusinessQuantityOperationResultVO idempotent = idempotentResult(tenantId, dto);
        if (idempotent != null) {
            return idempotent;
        }

        ensureBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        int updated = balanceMapper.lockQuantity(tenantId, dto.getAccountCode(), dto.getItemCode(),
                dto.getDimensionKey(), dto.getQuantity());
        if (updated <= 0) {
            throw new BusinessException("可用数量不足，锁定失败");
        }
        AiBusinessQuantityLock lock = insertLock(tenantId, dto);
        AiBusinessQuantityBalance balance = currentBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        AiBusinessQuantityLedger ledger = insertLedger(tenantId, dto, OP_LOCK, dto.getQuantity(), balance, lock.getId());
        return buildResult(dto, ledger, balance, null, false, "数量已锁定");
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO release(BusinessQuantityOperationDTO dto) {
        Long tenantId = resolveTenantId();
        normalizeCommon(dto, OP_RELEASE);
        BusinessQuantityOperationResultVO idempotent = idempotentResult(tenantId, dto);
        if (idempotent != null) {
            return idempotent;
        }

        AiBusinessQuantityLock lock = resolveLock(tenantId, dto);
        int lockUpdated = lockMapper.releaseLockQuantity(tenantId, lock.getId(), dto.getQuantity());
        if (lockUpdated <= 0) {
            throw new BusinessException("锁定数量不足，释放失败");
        }
        int balanceUpdated = balanceMapper.releaseLockedQuantity(tenantId, lock.getAccountCode(), lock.getItemCode(),
                lock.getDimensionKey(), dto.getQuantity());
        if (balanceUpdated <= 0) {
            throw new BusinessException("余额锁定数量不足，释放失败");
        }
        updateLockStatusIfFinished(tenantId, lock.getId(), STATUS_RELEASED);
        AiBusinessQuantityBalance balance = currentBalance(tenantId, lock.getAccountCode(), lock.getItemCode(), lock.getDimensionKey());
        syncDtoFromLock(dto, lock);
        AiBusinessQuantityLedger ledger = insertLedger(tenantId, dto, OP_RELEASE, -dto.getQuantity(), balance, lock.getId());
        return buildResult(dto, ledger, balance, null, false, "锁定数量已释放");
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO commit(BusinessQuantityOperationDTO dto) {
        Long tenantId = resolveTenantId();
        normalizeCommon(dto, OP_COMMIT);
        BusinessQuantityOperationResultVO idempotent = idempotentResult(tenantId, dto);
        if (idempotent != null) {
            return idempotent;
        }

        AiBusinessQuantityLock lock = resolveLock(tenantId, dto);
        int lockUpdated = lockMapper.commitLockQuantity(tenantId, lock.getId(), dto.getQuantity());
        if (lockUpdated <= 0) {
            throw new BusinessException("锁定数量不足，扣减失败");
        }
        int balanceUpdated = balanceMapper.commitLockedQuantity(tenantId, lock.getAccountCode(), lock.getItemCode(),
                lock.getDimensionKey(), dto.getQuantity());
        if (balanceUpdated <= 0) {
            throw new BusinessException("余额锁定数量不足，扣减失败");
        }
        updateLockStatusIfFinished(tenantId, lock.getId(), STATUS_COMMITTED);
        AiBusinessQuantityBalance balance = currentBalance(tenantId, lock.getAccountCode(), lock.getItemCode(), lock.getDimensionKey());
        syncDtoFromLock(dto, lock);
        AiBusinessQuantityLedger ledger = insertLedger(tenantId, dto, OP_COMMIT, -dto.getQuantity(), balance, lock.getId());
        return buildResult(dto, ledger, balance, null, false, "锁定数量已扣减");
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessQuantityOperationResultVO transfer(BusinessQuantityOperationDTO dto) {
        Long tenantId = resolveTenantId();
        normalizeCommon(dto, OP_TRANSFER);
        normalizeTarget(dto);
        BusinessQuantityOperationResultVO idempotent = idempotentResult(tenantId, dto);
        if (idempotent != null) {
            return idempotent;
        }
        if (sameQuantityKey(dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey(),
                dto.getTargetAccountCode(), dto.getTargetItemCode(), dto.getTargetDimensionKey())) {
            throw new BusinessException("转移源和目标数量维度不能相同");
        }

        ensureBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        ensureBalance(tenantId, dto.getTargetAccountCode(), dto.getTargetItemCode(), dto.getTargetDimensionKey());
        int sourceUpdated = balanceMapper.decreaseAvailableQuantity(tenantId, dto.getAccountCode(), dto.getItemCode(),
                dto.getDimensionKey(), dto.getQuantity());
        if (sourceUpdated <= 0) {
            throw new BusinessException("可用数量不足，转移失败");
        }
        int targetUpdated = balanceMapper.increaseQuantity(tenantId, dto.getTargetAccountCode(), dto.getTargetItemCode(),
                dto.getTargetDimensionKey(), dto.getQuantity());
        if (targetUpdated <= 0) {
            throw new BusinessException("目标数量账户不可用，转移失败");
        }
        AiBusinessQuantityBalance sourceBalance = currentBalance(tenantId, dto.getAccountCode(), dto.getItemCode(), dto.getDimensionKey());
        AiBusinessQuantityBalance targetBalance = currentBalance(tenantId, dto.getTargetAccountCode(), dto.getTargetItemCode(), dto.getTargetDimensionKey());
        AiBusinessQuantityLedger ledger = insertLedger(tenantId, dto, OP_TRANSFER, -dto.getQuantity(), sourceBalance,
                null, transferSourceIdempotencyKey(dto));
        AiBusinessQuantityLedger targetLedger = insertTransferTargetLedger(tenantId, dto, targetBalance);
        BusinessQuantityOperationResultVO result = buildResult(dto, ledger, sourceBalance, targetBalance, false, "数量已转移");
        result.setTargetLedgerId(targetLedger.getId());
        return result;
    }

    private void normalizeCommon(BusinessQuantityOperationDTO dto, String operationType) {
        if (dto == null) {
            throw new BusinessException("数量台账操作参数不能为空");
        }
        dto.setOperationType(operationType);
        dto.setAccountCode(requireText(dto.getAccountCode(), "数量账户编码不能为空"));
        dto.setItemCode(requireText(dto.getItemCode(), "数量项编码不能为空"));
        dto.setDimensionKey(StringUtils.trimToEmpty(dto.getDimensionKey()));
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new BusinessException("数量必须大于0");
        }
        dto.setIdempotencyKey(requireText(dto.getIdempotencyKey(), "数量台账幂等键不能为空"));
        int maxKeyLength = OP_TRANSFER.equals(operationType) ? 128 - TRANSFER_TARGET_SUFFIX.length() : 128;
        if (dto.getIdempotencyKey().length() > maxKeyLength) {
            throw new BusinessException("数量台账幂等键长度不能超过" + maxKeyLength);
        }
        dto.setCorrelationId(StringUtils.trimToNull(dto.getCorrelationId()));
        dto.setSourceObjectCode(StringUtils.trimToNull(dto.getSourceObjectCode()));
        dto.setSourceRecordId(StringUtils.trimToNull(dto.getSourceRecordId()));
        dto.setSourceDetailId(StringUtils.trimToNull(dto.getSourceDetailId()));
        dto.setRemark(StringUtils.trimToNull(dto.getRemark()));
    }

    private void normalizeTarget(BusinessQuantityOperationDTO dto) {
        dto.setTargetAccountCode(requireText(dto.getTargetAccountCode(), "目标数量账户编码不能为空"));
        dto.setTargetItemCode(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getTargetItemCode()), dto.getItemCode()));
        dto.setTargetDimensionKey(StringUtils.trimToEmpty(dto.getTargetDimensionKey()));
    }

    private BusinessQuantityOperationResultVO idempotentResult(Long tenantId, BusinessQuantityOperationDTO dto) {
        AiBusinessQuantityLedger ledger = ledgerMapper.selectByIdempotencyKey(tenantId, dto.getIdempotencyKey());
        if (ledger == null && OP_TRANSFER.equals(dto.getOperationType())) {
            ledger = ledgerMapper.selectByIdempotencyKey(tenantId, transferSourceIdempotencyKey(dto));
        }
        if (ledger == null) {
            return null;
        }
        AiBusinessQuantityLedger targetLedger = OP_TRANSFER.equals(ledger.getOperationType())
                ? ledgerMapper.selectByIdempotencyKey(tenantId, transferTargetIdempotencyKey(dto))
                : null;
        AiBusinessQuantityBalance balance = balanceMapper.selectByKey(tenantId, ledger.getAccountCode(),
                ledger.getItemCode(), ledger.getDimensionKey());
        AiBusinessQuantityBalance targetBalance = null;
        if (StringUtils.isNotBlank(ledger.getTargetAccountCode())) {
            targetBalance = balanceMapper.selectByKey(tenantId, ledger.getTargetAccountCode(),
                    ledger.getTargetItemCode(), StringUtils.defaultString(ledger.getTargetDimensionKey()));
        }
        BusinessQuantityOperationDTO snapshot = new BusinessQuantityOperationDTO();
        snapshot.setOperationType(ledger.getOperationType());
        snapshot.setAccountCode(ledger.getAccountCode());
        snapshot.setItemCode(ledger.getItemCode());
        snapshot.setDimensionKey(ledger.getDimensionKey());
        snapshot.setQuantity(Math.abs(ledger.getQuantityDelta() == null || ledger.getQuantityDelta() == 0
                ? dto.getQuantity()
                : ledger.getQuantityDelta()));
        snapshot.setTargetAccountCode(ledger.getTargetAccountCode());
        snapshot.setTargetItemCode(ledger.getTargetItemCode());
        snapshot.setTargetDimensionKey(ledger.getTargetDimensionKey());
        BusinessQuantityOperationResultVO result = buildResult(snapshot, ledger, balance, targetBalance, true, "数量台账操作已幂等命中");
        result.setBalanceQuantity(ledger.getBalanceQuantity());
        result.setLockedQuantity(ledger.getLockedQuantity());
        result.setAvailableQuantity(safeSubtract(ledger.getBalanceQuantity(), ledger.getLockedQuantity()));
        result.setTargetLedgerId(targetLedger == null ? null : targetLedger.getId());
        return result;
    }

    private AiBusinessQuantityBalance ensureBalance(Long tenantId, String accountCode, String itemCode, String dimensionKey) {
        AiBusinessQuantityBalance existing = balanceMapper.selectByKey(tenantId, accountCode, itemCode, dimensionKey);
        if (existing != null) {
            return existing;
        }
        AiBusinessQuantityBalance created = new AiBusinessQuantityBalance();
        created.setTenantId(tenantId);
        created.setAccountCode(accountCode);
        created.setItemCode(itemCode);
        created.setDimensionKey(dimensionKey);
        created.setQuantity(0L);
        created.setLockedQuantity(0L);
        created.setStatus(EnableStatus.ENABLED.getCode());
        try {
            balanceMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return currentBalance(tenantId, accountCode, itemCode, dimensionKey);
        }
    }

    private AiBusinessQuantityLock insertLock(Long tenantId, BusinessQuantityOperationDTO dto) {
        AiBusinessQuantityLock lock = new AiBusinessQuantityLock();
        lock.setTenantId(tenantId);
        lock.setLockCode(StringUtils.trimToNull(dto.getLockCode()));
        lock.setAccountCode(dto.getAccountCode());
        lock.setItemCode(dto.getItemCode());
        lock.setDimensionKey(dto.getDimensionKey());
        lock.setLockQuantity(dto.getQuantity());
        lock.setReleasedQuantity(0L);
        lock.setCommittedQuantity(0L);
        lock.setLockStatus(STATUS_LOCKED);
        lock.setSourceObjectCode(dto.getSourceObjectCode());
        lock.setSourceRecordId(dto.getSourceRecordId());
        lock.setSourceDetailId(dto.getSourceDetailId());
        lock.setCorrelationId(dto.getCorrelationId());
        lock.setIdempotencyKey(dto.getIdempotencyKey());
        lock.setRemark(dto.getRemark());
        lockMapper.insert(lock);
        return lock;
    }

    private AiBusinessQuantityLedger insertLedger(Long tenantId,
                                                  BusinessQuantityOperationDTO dto,
                                                  String operationType,
                                                  Long quantityDelta,
                                                  AiBusinessQuantityBalance balance,
                                                  Long lockId) {
        return insertLedger(tenantId, dto, operationType, quantityDelta, balance, lockId, dto.getIdempotencyKey());
    }

    private AiBusinessQuantityLedger insertLedger(Long tenantId,
                                                  BusinessQuantityOperationDTO dto,
                                                  String operationType,
                                                  Long quantityDelta,
                                                  AiBusinessQuantityBalance balance,
                                                  Long lockId,
                                                  String idempotencyKey) {
        AiBusinessQuantityLedger ledger = new AiBusinessQuantityLedger();
        ledger.setTenantId(tenantId);
        ledger.setOperationType(operationType);
        ledger.setAccountCode(dto.getAccountCode());
        ledger.setItemCode(dto.getItemCode());
        ledger.setDimensionKey(dto.getDimensionKey());
        ledger.setQuantityDelta(quantityDelta);
        ledger.setBalanceQuantity(safeLong(balance == null ? null : balance.getQuantity()));
        ledger.setLockedQuantity(safeLong(balance == null ? null : balance.getLockedQuantity()));
        ledger.setTargetAccountCode(StringUtils.trimToNull(dto.getTargetAccountCode()));
        ledger.setTargetItemCode(StringUtils.trimToNull(dto.getTargetItemCode()));
        ledger.setTargetDimensionKey(StringUtils.trimToNull(dto.getTargetDimensionKey()));
        ledger.setSourceObjectCode(dto.getSourceObjectCode());
        ledger.setSourceRecordId(dto.getSourceRecordId());
        ledger.setSourceDetailId(dto.getSourceDetailId());
        ledger.setLockId(lockId);
        ledger.setCorrelationId(dto.getCorrelationId());
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setRemark(dto.getRemark());
        ledger.setExtraData(writeJson(dto.getExtraData()));
        ledgerMapper.insert(ledger);
        return ledger;
    }

    private AiBusinessQuantityLedger insertTransferTargetLedger(Long tenantId,
                                                               BusinessQuantityOperationDTO dto,
                                                               AiBusinessQuantityBalance targetBalance) {
        AiBusinessQuantityLedger ledger = new AiBusinessQuantityLedger();
        ledger.setTenantId(tenantId);
        ledger.setOperationType(OP_TRANSFER);
        ledger.setAccountCode(dto.getTargetAccountCode());
        ledger.setItemCode(dto.getTargetItemCode());
        ledger.setDimensionKey(dto.getTargetDimensionKey());
        ledger.setQuantityDelta(dto.getQuantity());
        ledger.setBalanceQuantity(safeLong(targetBalance == null ? null : targetBalance.getQuantity()));
        ledger.setLockedQuantity(safeLong(targetBalance == null ? null : targetBalance.getLockedQuantity()));
        ledger.setTargetAccountCode(StringUtils.trimToNull(dto.getTargetAccountCode()));
        ledger.setTargetItemCode(StringUtils.trimToNull(dto.getTargetItemCode()));
        ledger.setTargetDimensionKey(StringUtils.trimToNull(dto.getTargetDimensionKey()));
        ledger.setSourceObjectCode(dto.getSourceObjectCode());
        ledger.setSourceRecordId(dto.getSourceRecordId());
        ledger.setSourceDetailId(dto.getSourceDetailId());
        ledger.setCorrelationId(dto.getCorrelationId());
        ledger.setIdempotencyKey(transferTargetIdempotencyKey(dto));
        ledger.setRemark(dto.getRemark());
        Map<String, Object> extraData = new LinkedHashMap<>(dto.getExtraData() == null ? Map.of() : dto.getExtraData());
        extraData.put("transferSide", "TARGET");
        extraData.put("sourceAccountCode", dto.getAccountCode());
        extraData.put("sourceItemCode", dto.getItemCode());
        extraData.put("sourceDimensionKey", dto.getDimensionKey());
        ledger.setExtraData(writeJson(extraData));
        ledgerMapper.insert(ledger);
        return ledger;
    }

    private AiBusinessQuantityLock resolveLock(Long tenantId, BusinessQuantityOperationDTO dto) {
        AiBusinessQuantityLock lock = null;
        if (dto.getLockId() != null) {
            lock = lockMapper.selectById(dto.getLockId());
            if (lock != null && !tenantId.equals(lock.getTenantId())) {
                lock = null;
            }
        }
        if (lock == null && StringUtils.isNotBlank(dto.getLockCode())) {
            lock = lockMapper.selectByLockCode(tenantId, dto.getLockCode());
        }
        if (lock == null && StringUtils.isNotBlank(dto.getSourceObjectCode()) && StringUtils.isNotBlank(dto.getSourceRecordId())) {
            lock = lockMapper.selectActiveBySource(tenantId, dto.getAccountCode(), dto.getItemCode(),
                    dto.getDimensionKey(), dto.getSourceObjectCode(), dto.getSourceRecordId(), dto.getSourceDetailId());
        }
        if (lock == null) {
            throw new BusinessException("未找到可用数量锁定记录");
        }
        if (!STATUS_LOCKED.equals(lock.getLockStatus())) {
            throw new BusinessException("数量锁定记录不是可操作状态");
        }
        return lock;
    }

    private void updateLockStatusIfFinished(Long tenantId, Long lockId, String finishStatus) {
        AiBusinessQuantityLock latest = lockMapper.selectById(lockId);
        long remaining = safeLong(latest.getLockQuantity())
                - safeLong(latest.getReleasedQuantity())
                - safeLong(latest.getCommittedQuantity());
        if (remaining <= 0) {
            lockMapper.updateLockStatus(tenantId, lockId, finishStatus);
        }
    }

    private void syncDtoFromLock(BusinessQuantityOperationDTO dto, AiBusinessQuantityLock lock) {
        dto.setAccountCode(lock.getAccountCode());
        dto.setItemCode(lock.getItemCode());
        dto.setDimensionKey(lock.getDimensionKey());
        if (StringUtils.isBlank(dto.getSourceObjectCode())) {
            dto.setSourceObjectCode(lock.getSourceObjectCode());
        }
        if (StringUtils.isBlank(dto.getSourceRecordId())) {
            dto.setSourceRecordId(lock.getSourceRecordId());
        }
        if (StringUtils.isBlank(dto.getSourceDetailId())) {
            dto.setSourceDetailId(lock.getSourceDetailId());
        }
        if (StringUtils.isBlank(dto.getCorrelationId())) {
            dto.setCorrelationId(lock.getCorrelationId());
        }
    }

    private AiBusinessQuantityBalance currentBalance(Long tenantId, String accountCode, String itemCode, String dimensionKey) {
        AiBusinessQuantityBalance balance = balanceMapper.selectByKey(tenantId, accountCode, itemCode, dimensionKey);
        if (balance == null) {
            throw new BusinessException("数量余额不存在");
        }
        return balance;
    }

    private BusinessQuantityOperationResultVO buildResult(BusinessQuantityOperationDTO dto,
                                                          AiBusinessQuantityLedger ledger,
                                                          AiBusinessQuantityBalance balance,
                                                          AiBusinessQuantityBalance targetBalance,
                                                          boolean idempotentHit,
                                                          String message) {
        BusinessQuantityOperationResultVO result = new BusinessQuantityOperationResultVO();
        result.setOperationType(dto.getOperationType());
        result.setAccountCode(dto.getAccountCode());
        result.setItemCode(dto.getItemCode());
        result.setDimensionKey(dto.getDimensionKey());
        result.setQuantity(dto.getQuantity());
        result.setBalanceQuantity(safeLong(balance == null ? null : balance.getQuantity()));
        result.setLockedQuantity(safeLong(balance == null ? null : balance.getLockedQuantity()));
        result.setAvailableQuantity(safeSubtract(result.getBalanceQuantity(), result.getLockedQuantity()));
        result.setTargetAccountCode(dto.getTargetAccountCode());
        result.setTargetItemCode(dto.getTargetItemCode());
        result.setTargetDimensionKey(dto.getTargetDimensionKey());
        result.setTargetBalanceQuantity(targetBalance == null ? null : safeLong(targetBalance.getQuantity()));
        result.setTargetLockedQuantity(targetBalance == null ? null : safeLong(targetBalance.getLockedQuantity()));
        result.setTargetAvailableQuantity(targetBalance == null ? null
                : safeSubtract(targetBalance.getQuantity(), targetBalance.getLockedQuantity()));
        result.setLedgerId(ledger == null ? null : ledger.getId());
        result.setLockId(ledger == null ? null : ledger.getLockId());
        result.setIdempotentHit(idempotentHit);
        result.setMessage(message);
        return result;
    }

    private String normalizeOperationType(String operationType) {
        return StringUtils.defaultString(operationType)
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace("-", "_")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new BusinessException(message);
        }
        return text;
    }

    private boolean sameQuantityKey(String accountCode,
                                    String itemCode,
                                    String dimensionKey,
                                    String targetAccountCode,
                                    String targetItemCode,
                                    String targetDimensionKey) {
        return StringUtils.equals(accountCode, targetAccountCode)
                && StringUtils.equals(itemCode, targetItemCode)
                && StringUtils.equals(StringUtils.defaultString(dimensionKey), StringUtils.defaultString(targetDimensionKey));
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private long safeSubtract(Long value, Long subtract) {
        return safeLong(value) - safeLong(subtract);
    }

    private String transferSourceIdempotencyKey(BusinessQuantityOperationDTO dto) {
        return dto.getIdempotencyKey() + TRANSFER_SOURCE_SUFFIX;
    }

    private String transferTargetIdempotencyKey(BusinessQuantityOperationDTO dto) {
        return dto.getIdempotencyKey() + TRANSFER_TARGET_SUFFIX;
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("数量台账扩展数据序列化失败", e);
            return null;
        }
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId == null ? 1L : tenantId;
    }
}
