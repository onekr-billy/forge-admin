package com.mdframe.forge.starter.flow.event;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Outbox 中持久化的本地镜像投影快照。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowProjectionOutboxPayload {

    private FlowProjectionType type;
    private FlowTask task;
    private FlowBusiness business;
    private String formStatus;

    public static FlowProjectionOutboxPayload fromEvent(FlowProjectionEvent event) {
        return new FlowProjectionOutboxPayload(
                event.getType(), event.getTask(), event.getBusiness(), event.getFormStatus());
    }
}
