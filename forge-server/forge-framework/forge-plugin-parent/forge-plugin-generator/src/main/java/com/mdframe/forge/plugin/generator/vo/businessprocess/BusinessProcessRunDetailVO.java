package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务流程运行详情与节点安全时间线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessProcessRunDetailVO extends BusinessProcessRunVO {

    private String flowProcessInstanceId;

    private List<NodeRunVO> timeline = new ArrayList<>();

    /**
     * 节点尝试只暴露脱敏摘要，不暴露幂等键或完整输入输出正文。
     */
    @Data
    public static class NodeRunVO {

        private String id;

        private String runId;

        private String nodeId;

        /** 从流程 schema 解析的节点名称。 */
        private String nodeName;

        private String nodeType;

        private Integer attemptNo;

        private String status;

        private String correlationId;

        private String inputSummary;

        private String outputSummary;

        private String errorCode;

        private String errorSummary;

        private LocalDateTime nextRetryTime;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;
    }
}
