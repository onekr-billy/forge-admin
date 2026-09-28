package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.enums.LowcodePublishTaskStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** HTTP 在线发布的持久化入口；先提交任务，再同步推进到配置提交。 */
@Service
@RequiredArgsConstructor
public class LowcodeOnlinePublishCoordinator {

    private final LowcodePublishTaskService taskService;
    private final LowcodeOnlinePublishWorkflowExecutor workflowExecutor;

    public Long publish(LowcodeOnlinePublishPlan plan) {
        String owner = "request-" + UUID.randomUUID();
        AiLowcodePublishTask task = taskService.stageOnlinePublish(plan, owner);
        LowcodeOnlinePublishCommand command = taskService.restoreOnlinePublish(task);
        if (LowcodePublishTaskStatus.COMPLETED.matches(task.getTaskStatus())) {
            return command.versionId();
        }
        if (LowcodePublishTaskStatus.SUPERSEDED.matches(task.getTaskStatus())) {
            throw new BusinessException("在线发布任务已被更新版本替代");
        }
        if (LowcodePublishTaskStatus.DEAD.matches(task.getTaskStatus())) {
            throw new BusinessException("在线发布任务已进入死信，请人工检查后重放");
        }
        AiLowcodePublishTask claimed = task;
        if (!LowcodePublishTaskStatus.PROCESSING.matches(task.getTaskStatus())
                || !owner.equals(task.getLockOwner())) {
            claimed = taskService.claim(task, owner, LocalDateTime.now());
        }
        if (claimed == null) {
            throw new BusinessException("相同在线发布任务正在处理中，请稍后重试");
        }
        try {
            LowcodeOnlinePublishWorkflowExecutor.Result result =
                    workflowExecutor.execute(claimed, true);
            if (result == LowcodeOnlinePublishWorkflowExecutor.Result.SUPERSEDED) {
                taskService.markCompleted(claimed, true);
                throw new BusinessException("在线发布期间草稿或发布版本已变化，请重新发起");
            }
            if (result == LowcodeOnlinePublishWorkflowExecutor.Result.COMPLETED) {
                taskService.markCompleted(claimed, false);
            }
            return command.versionId();
        } catch (Exception failure) {
            if (LowcodePublishTaskStatus.PROCESSING.matches(claimed.getTaskStatus())) {
                try {
                    taskService.markFailed(claimed, failure);
                } catch (Exception ignored) {
                    // 原始失败优先返回；租约过期后仍可由扫描器恢复。
                }
            }
            if (failure instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("在线发布任务执行失败，请稍后重试");
        }
    }
}
