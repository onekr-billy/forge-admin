package com.mdframe.forge.starter.flow.vo;

import lombok.Data;
import java.util.List;

@Data
public class VersionCompareVO {
    private List<NodeDiff> addedNodes;
    private List<NodeDiff> modifiedNodes;
    private List<NodeDiff> deletedNodes;
    private List<FlowDiff> addedFlows;
    private List<FlowDiff> modifiedFlows;
    private List<FlowDiff> deletedFlows;

    @Data
    public static class NodeDiff {
        private String id;
        private String name;
        private String oldName;
        private String newName;
    }

    @Data
    public static class FlowDiff {
        private String id;
        private String source;
        private String target;
        private String oldSource;
        private String newSource;
        private String oldTarget;
        private String newTarget;
    }
}