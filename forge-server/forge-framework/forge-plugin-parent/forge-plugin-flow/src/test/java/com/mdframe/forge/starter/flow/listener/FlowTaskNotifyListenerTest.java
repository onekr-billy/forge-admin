package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.plugin.message.domain.MessageSendStatus;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.service.FlowTaskReceiverResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowTaskNotifyListenerTest {

    @Test
    void failedMessageDeliveryPropagatesToOutboxDispatcher() {
        MessageService messageService = mock(MessageService.class);
        FlowTaskReceiverResolver receiverResolver = mock(FlowTaskReceiverResolver.class);
        FlowTaskNotifyListener listener = new FlowTaskNotifyListener();
        ReflectionTestUtils.setField(listener, "messageService", messageService);
        ReflectionTestUtils.setField(listener, "taskReceiverResolver", receiverResolver);

        FlowTask task = new FlowTask();
        task.setTaskId("task-1");
        task.setTitle("审批任务");
        task.setTenantId(1L);
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        business.setProcessInstanceId("process-1");
        when(receiverResolver.resolveReceivers(task)).thenReturn(Set.of(9L));
        SysMessage failed = new SysMessage();
        failed.setStatus(MessageSendStatus.FAILED.getCode());
        when(messageService.sendIfAbsent(any(), anyString(), anyString())).thenReturn(failed);

        FlowTaskNotifyEvent event = FlowTaskNotifyEvent.todo(task, business);

        assertThrows(IllegalStateException.class, () -> listener.handle(event));
    }
}
