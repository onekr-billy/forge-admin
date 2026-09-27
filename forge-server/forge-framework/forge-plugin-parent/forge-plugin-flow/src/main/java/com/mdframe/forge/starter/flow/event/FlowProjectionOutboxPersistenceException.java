package com.mdframe.forge.starter.flow.event;

/** 投影 Outbox 无法持久化时中止 Flowable 事务，避免留下不可恢复的镜像缺口。 */
public class FlowProjectionOutboxPersistenceException extends RuntimeException {

    public FlowProjectionOutboxPersistenceException(Throwable cause) {
        super("FLOW_PROJECTION_OUTBOX_PERSISTENCE_FAILED", cause);
    }
}
