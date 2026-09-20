package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务单据运行态视图。
 */
@Data
public class BusinessDocumentRuntimeVO {

    private Boolean documentEnabled;

    private String documentStatus;

    private String documentStatusLabel;

    private String businessKey;

    private String flowStatus;

    private String processInstanceId;

    /** 当前流程实例的提交轮次，从 1 开始。 */
    private Integer roundNo;

    /** 同一单据的历史提交轮次，按 roundNo 升序。 */
    private List<FlowRoundVO> flowRounds = new ArrayList<>();

    private Boolean detailFlowTimelineVisible;

    private Boolean detailFlowDiagramVisible;

    private List<String> availableActions = new ArrayList<>();

    private List<RuntimeActionVO> runtimeActions = new ArrayList<>();

    /** 当前业务记录正在运行的应用级业务流程编码。 */
    private List<String> activeProcessCodes = new ArrayList<>();

    /** 当前业务记录已占用启动入口的应用级流程编码（失败可重试记录除外）。 */
    private List<String> startedProcessCodes = new ArrayList<>();

    private String nextAction;

    private String message;

    /** 当前登录人在这条单据上的待办，没有待办时为 null。 */
    private MyTaskVO myTask;

    /** 当前登录人在这条单据上的待办。 */
    @Data
    public static class MyTaskVO {

        private String taskId;

        private String taskDefKey;

        private String taskName;

        private String processInstanceId;

        /** 是否为驳回后的发起人修改节点，决定单据页给「修改后重提」还是「去处理」。 */
        private Boolean initiatorModify;
    }

    /** 单据的一次流程提交轮次。 */
    @Data
    public static class FlowRoundVO {

        private Integer roundNo;

        private String processInstanceId;

        private String flowStatus;

        private String result;
    }

    @Data
    public static class RuntimeActionVO {

        private String key;

        private String label;

        private String type;

        private String actionType;

        private Boolean visible;

        private Boolean disabled;

        private String disabledReason;

        private String objectCode;

        private Long recordId;
    }
}
