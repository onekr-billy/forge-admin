package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.event.FlowNotifyOutboxPersistenceException;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 在发布线程的本地事务中先持久化通知意图。 */
@Component
@RequiredArgsConstructor
public class FlowNotifyOutboxCaptureListener {

    private final FlowNotifyOutboxService outboxService;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void capture(FlowTaskNotifyEvent event) {
        try {
            outboxService.append(event);
        } catch (RuntimeException failure) {
            throw new FlowNotifyOutboxPersistenceException(failure);
        }
    }
}
