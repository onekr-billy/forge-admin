package com.mdframe.forge.starter.flow.event;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Flowable 事件对应的本地镜像投影信封。 */
@Getter
public class FlowProjectionEvent {

    public static final int CURRENT_VERSION = 1;

    private final String eventId;
    private final Integer eventVersion;
    private final LocalDateTime occurredAt;
    private final Long tenantId;
    private final FlowProjectionType type;
    private final String aggregateType;
    private final String aggregateId;
    private final FlowTask task;
    private final FlowBusiness business;
    private final String formStatus;

    private FlowProjectionEvent(Long tenantId, FlowProjectionType type, String aggregateType,
                                String aggregateId, FlowTask task, FlowBusiness business,
                                String formStatus) {
        this.eventId = UUID.randomUUID().toString();
        this.eventVersion = CURRENT_VERSION;
        this.occurredAt = LocalDateTime.now();
        this.tenantId = tenantId;
        this.type = type;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.task = task;
        this.business = business;
        this.formStatus = formStatus;
    }

    public static FlowProjectionEvent task(FlowProjectionType type, FlowTask task) {
        Long tenantId = task == null ? null : task.getTenantId();
        String taskId = task == null ? null : task.getTaskId();
        return new FlowProjectionEvent(tenantId, type, "TASK", taskId, task, null, null);
    }

    public static FlowProjectionEvent process(FlowProjectionType type, FlowBusiness business,
                                               String formStatus) {
        Long tenantId = business == null ? null : business.getTenantId();
        String processInstanceId = business == null ? null : business.getProcessInstanceId();
        return new FlowProjectionEvent(tenantId, type, "PROCESS", processInstanceId,
                null, business, formStatus);
    }
}
