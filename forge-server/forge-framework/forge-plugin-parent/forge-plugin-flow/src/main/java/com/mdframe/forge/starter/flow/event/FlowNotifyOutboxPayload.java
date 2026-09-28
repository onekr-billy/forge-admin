package com.mdframe.forge.starter.flow.event;

import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Outbox 中保存的流程通知业务快照。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowNotifyOutboxPayload {

    private FlowTaskNotifyEvent.Type type;
    private FlowTask flowTask;
    private FlowBusiness business;
    private String taskId;
    private FlowEventMessage eventMessage;
    private String processDefKey;
    private Map<String, Object> variables;
    private Boolean rejected;

    public static FlowNotifyOutboxPayload fromEvent(FlowTaskNotifyEvent event) {
        return new FlowNotifyOutboxPayload(event.getType(), event.getFlowTask(), event.getBusiness(),
                event.getTaskId(), event.getEventMessage(), event.getProcessDefKey(),
                event.getVariables(), event.getRejected());
    }

    public FlowTaskNotifyEvent toEvent(String eventId, Integer eventVersion, LocalDateTime occurredAt,
                                       Long tenantId, Long eventSequence) {
        if (eventMessage != null) {
            eventMessage.setEventId(eventId);
            eventMessage.setEventVersion(eventVersion);
            eventMessage.setEventSequence(eventSequence);
        }
        return FlowTaskNotifyEvent.restore(eventId, eventVersion, occurredAt, tenantId, type,
                flowTask, business, taskId, eventMessage, processDefKey, variables, rejected);
    }
}
