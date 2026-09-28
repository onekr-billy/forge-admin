package com.mdframe.forge.flow.vo;

import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import lombok.Data;

import java.time.LocalDateTime;

/** 不暴露通知载荷的流程通知死信摘要。 */
@Data
public class FlowNotifyDeadLetterVO {

    private Long id;
    private String eventId;
    private Integer eventVersion;
    private LocalDateTime occurredAt;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private Integer retryCount;
    private String lastError;
    private Integer replayCount;
    private String replayedBy;
    private LocalDateTime replayedTime;
    private String replayReason;
    private LocalDateTime updateTime;

    public static FlowNotifyDeadLetterVO from(FlowNotifyOutbox outbox) {
        FlowNotifyDeadLetterVO result = new FlowNotifyDeadLetterVO();
        result.setId(outbox.getId());
        result.setEventId(outbox.getEventId());
        result.setEventVersion(outbox.getEventVersion());
        result.setOccurredAt(outbox.getOccurredAt());
        result.setEventType(outbox.getEventType());
        result.setAggregateType(outbox.getAggregateType());
        result.setAggregateId(outbox.getAggregateId());
        result.setRetryCount(outbox.getRetryCount());
        result.setLastError(outbox.getLastError());
        result.setReplayCount(outbox.getReplayCount());
        result.setReplayedBy(outbox.getReplayedBy());
        result.setReplayedTime(outbox.getReplayedTime());
        result.setReplayReason(outbox.getReplayReason());
        result.setUpdateTime(outbox.getUpdateTime());
        return result;
    }
}
