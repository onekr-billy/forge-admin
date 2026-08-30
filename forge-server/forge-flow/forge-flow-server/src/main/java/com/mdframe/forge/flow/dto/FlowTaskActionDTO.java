package com.mdframe.forge.flow.dto;

import com.mdframe.forge.starter.flow.dto.FlowApprovalPointResultDTO;
import lombok.Data;

import java.util.List;

/**
 * 流程任务办理公共请求。
 */
@Data
public class FlowTaskActionDTO {

    private String taskId;

    private String userId;

    private String comment;

    private String signature;

    /**
     * 指定退回的用户任务定义Key。为空时保持退回上一审批节点的兼容行为。
     */
    private String targetActivityId;

    /**
     * 退回节点修正后是否跳过中间节点，直接送回原驳回节点。
     */
    private Boolean directSend;

    /**
     * 审批要点勾选结果。
     */
    private List<FlowApprovalPointResultDTO> approvalPointResults;
}
