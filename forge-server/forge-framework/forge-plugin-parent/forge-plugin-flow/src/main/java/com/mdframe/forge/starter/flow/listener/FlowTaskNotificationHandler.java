package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;

/** Outbox 成功认领后执行具体流程通知副作用。 */
public interface FlowTaskNotificationHandler {

    void handle(FlowTaskNotifyEvent event);
}
