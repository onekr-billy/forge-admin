package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowRemoteCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 接管超时及失败的流程启动命令，并在原租户上下文中补齐本地状态。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business.flow-remote-command", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessFlowRemoteCommandDispatcher {

    private final BusinessFlowRemoteCommandService commandService;
    private final ObjectProvider<BusinessFlowService> businessFlowServiceProvider;

    @Value("${forge.business.flow-remote-command.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${forge.business.flow-remote-command.scan-interval-ms:5000}")
    public void recover() {
        BusinessFlowService businessFlowService = businessFlowServiceProvider.getIfAvailable();
        if (businessFlowService == null) {
            return;
        }
        for (AiBusinessFlowRemoteCommand command : commandService.findRecoveryCandidates(
                LocalDateTime.now(), Math.max(1, batchSize))) {
            try {
                businessFlowService.recoverRemoteStartCommand(command.getTenantId(), command.getId());
            } catch (RuntimeException failure) {
                try {
                    commandService.recordRecoveryFailure(command.getTenantId(), command.getId(), failure);
                } catch (RuntimeException stateFailure) {
                    log.error("流程远程命令恢复失败且无法更新状态: commandId={}, failureType={}",
                            command.getId(), stateFailure.getClass().getSimpleName());
                }
                log.warn("流程远程命令恢复失败: commandId={}, failureType={}",
                        command.getId(), failure.getClass().getSimpleName());
            }
        }
    }
}
