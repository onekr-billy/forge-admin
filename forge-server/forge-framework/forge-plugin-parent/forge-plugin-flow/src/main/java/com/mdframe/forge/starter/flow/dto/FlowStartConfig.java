package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程发起前需要由申请人补充的运行参数。
 */
@Data
public class FlowStartConfig {

    private String modelKey;

    private List<ApproverNode> initiatorSelectNodes = new ArrayList<>();

    @Data
    public static class ApproverNode {
        private String nodeKey;
        private String nodeName;
        private Boolean multiple;
    }
}
