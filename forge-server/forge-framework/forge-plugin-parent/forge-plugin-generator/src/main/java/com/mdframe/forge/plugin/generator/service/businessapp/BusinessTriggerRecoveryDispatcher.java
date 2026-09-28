package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 恢复因进程退出或瞬时失败而停留在 PENDING/FAILED 的业务触发器命令。
 *
 * <p>只有具备平台幂等边界的动作自动重放；创建记录和 Webhook 这类结果不确定的
 * 外部副作用转为 TODO，避免恢复任务制造重复数据或重复扣减。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business.trigger-recovery", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessTriggerRecoveryDispatcher {

    private final BusinessTriggerService triggerService;
    private final BusinessTriggerExecutor triggerExecutor;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.business.trigger-recovery.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${forge.business.trigger-recovery.scan-interval-ms:60000}")
    public void recoverPendingExecutions() {
        List<AiBusinessTriggerLog> candidates = triggerService.findRecoveryCandidates(
                LocalDateTime.now(), Math.max(1, batchSize));
        for (AiBusinessTriggerLog candidate : candidates) {
            recover(candidate);
        }
    }

    void recover(AiBusinessTriggerLog candidate) {
        AiBusinessTriggerLog claimed = triggerService.claimRecovery(candidate, workerId, LocalDateTime.now());
        if (claimed == null) {
            return;
        }
        try {
            BusinessTriggerExecutionEnvelope.ExecutionCommand command =
                    BusinessTriggerExecutionEnvelope.restore(claimed);
            if (!BusinessTriggerExecutionEnvelope.isReplaySafe(command.trigger())) {
                triggerService.markManualReview(claimed,
                        "执行结果不确定且动作不支持安全自动重放，请人工核对副作用后处理");
                return;
            }
            TenantContextHolder.executeWithTenant(claimed.getTenantId(),
                    () -> triggerExecutor.executeRecoveredTrigger(command.trigger(), command.event(), claimed));
        } catch (Exception failure) {
            try {
                triggerService.markManualReview(claimed,
                        "恢复命令校验失败: " + safeFailureType(failure));
            } catch (Exception recordFailure) {
                log.error("业务触发器恢复失败且无法记录人工处理状态: logId={}, failureType={}",
                        claimed.getId(), safeFailureType(recordFailure));
            }
            log.warn("业务触发器恢复命令拒绝执行: logId={}, failureType={}",
                    claimed.getId(), safeFailureType(failure));
        }
    }

    private String safeFailureType(Throwable failure) {
        String simpleName = failure == null ? null : failure.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "UnknownFailure" : simpleName;
    }
}
