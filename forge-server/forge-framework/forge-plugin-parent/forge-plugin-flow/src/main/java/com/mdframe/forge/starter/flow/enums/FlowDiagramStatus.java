package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程图及节点展示状态。
 */
@Getter
public enum FlowDiagramStatus {

    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    TERMINATED("terminated"),
    SKIPPED("skipped");

    private final String code;

    FlowDiagramStatus(String code) {
        this.code = code;
    }
}
