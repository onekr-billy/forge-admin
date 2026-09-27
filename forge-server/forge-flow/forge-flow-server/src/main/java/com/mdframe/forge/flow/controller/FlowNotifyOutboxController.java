package com.mdframe.forge.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.flow.dto.FlowNotifyOutboxReplayDTO;
import com.mdframe.forge.flow.vo.FlowNotifyDeadLetterPageVO;
import com.mdframe.forge.flow.vo.FlowNotifyDeadLetterVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 流程通知死信查看与人工重放。 */
@RestController
@RequestMapping("/api/flow/monitor/notify-outbox")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class FlowNotifyOutboxController {

    private final FlowNotifyOutboxService outboxService;

    @SaCheckPermission("flow:monitor:view")
    @GetMapping("/dead-letters")
    public RespInfo<FlowNotifyDeadLetterPageVO> pageDeadLetters(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<FlowNotifyOutbox> page = outboxService.pageDeadLetters(
                new Page<>(safePageNum, safePageSize));
        FlowNotifyDeadLetterPageVO result = new FlowNotifyDeadLetterPageVO();
        result.setList(page.getRecords().stream().map(FlowNotifyDeadLetterVO::from).toList());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return RespInfo.success(result);
    }

    @SaCheckPermission("flow:monitor:manage")
    @PostMapping("/{outboxId}/replay")
    public RespInfo<FlowNotifyDeadLetterVO> replay(
            @PathVariable Long outboxId,
            @Valid @RequestBody FlowNotifyOutboxReplayDTO dto) {
        LoginUser loginUser = SessionHelper.getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(403, "无法确定重放操作人");
        }
        FlowNotifyOutbox outbox = outboxService.requeueDeadLetter(
                outboxId, String.valueOf(loginUser.getUserId()), dto.getReason());
        return RespInfo.success("通知死信已进入重试队列", FlowNotifyDeadLetterVO.from(outbox));
    }
}
