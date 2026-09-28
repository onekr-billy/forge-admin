package com.mdframe.forge.starter.flow.enums;

/** Flowable 事件向 Forge 本地镜像投影的命令类型。 */
public enum FlowProjectionType {
    TASK_CREATED,
    TASK_COMPLETED,
    TASK_ASSIGNED,
    TASK_CANCELED,
    PROCESS_COMPLETED,
    PROCESS_REJECTED,
    PROCESS_CANCELED
}
