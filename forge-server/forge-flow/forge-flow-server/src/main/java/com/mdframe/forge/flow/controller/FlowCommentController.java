package com.mdframe.forge.flow.controller;

import com.mdframe.forge.flow.dto.FlowCommentAddDTO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.flow.entity.FlowComment;
import com.mdframe.forge.starter.flow.service.FlowCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程审批意见接口
 */
@RestController
@RequestMapping("/api/flow/comment")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
@IgnoreTenant
public class FlowCommentController {

    private final FlowCommentService flowCommentService;

    /**
     * 添加审批意见
     */
    @PostMapping
    public RespInfo<FlowComment> addComment(@RequestBody FlowCommentAddDTO dto) {
        FlowComment comment = flowCommentService.addComment(
                dto.getProcessInstanceId(), dto.getProcessDefKey(),
                dto.getTaskId(), dto.getTaskName(), dto.getType(),
                dto.getMessage(), dto.getUserId(), dto.getUserName());
        return RespInfo.success("添加成功", comment);
    }

    /**
     * 获取流程的所有审批意见（审批历史）
     */
    @GetMapping("/process/{processInstanceId}")
    public RespInfo<List<FlowComment>> getByProcessInstanceId(@PathVariable String processInstanceId) {
        List<FlowComment> comments = flowCommentService.getCommentsByProcessInstanceId(processInstanceId);
        return RespInfo.success(comments);
    }

    /**
     * 获取任务的审批意见
     */
    @GetMapping("/task/{taskId}")
    public RespInfo<List<FlowComment>> getByTaskId(@PathVariable String taskId) {
        List<FlowComment> comments = flowCommentService.getCommentsByTaskId(taskId);
        return RespInfo.success(comments);
    }

    /**
     * 添加流程事件
     */
    @PostMapping("/event")
    public RespInfo<FlowComment> addEvent(@RequestBody FlowCommentAddDTO dto) {
        FlowComment event = flowCommentService.addEvent(
                dto.getProcessInstanceId(), dto.getProcessDefKey(),
                dto.getMessage(), dto.getUserId(), dto.getUserName());
        return RespInfo.success("添加成功", event);
    }
}
