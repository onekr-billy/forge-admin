package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 定时恢复失败或租约超时的 Flow 回调消费。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business.flow-callback-inbox", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessFlowCallbackInboxDispatcher {

    private final BusinessFlowCallbackInboxService inboxService;
    private final BusinessFlowEngineEventConsumer consumer;

    @Value("${forge.business.flow-callback-inbox.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${forge.business.flow-callback-inbox.scan-interval-ms:30000}")
    public void recover() {
        for (AiBusinessFlowCallbackInbox candidate : inboxService.findRecoveryCandidates(
                LocalDateTime.now(), Math.max(1, batchSize))) {
            try {
                consumer.process(candidate);
            } catch (RuntimeException ignored) {
                // consumer 已记录失败类型并更新 Inbox 状态，下一轮按退避时间恢复。
            }
        }
    }
}
