package com.mdframe.forge.starter.flow.event;

/**
 * 通知意图无法写入 Outbox。
 *
 * <p>该异常必须越过 Flowable 事件监听器的历史降级边界，使当前引擎事务回滚，
 * 避免业务状态已经提交但通知意图永久丢失。</p>
 */
public class FlowNotifyOutboxPersistenceException extends RuntimeException {

    public FlowNotifyOutboxPersistenceException(Throwable cause) {
        super("FLOW_NOTIFY_OUTBOX_PERSIST_FAILED", cause);
    }
}
