package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 恢复低代码发布后置同步的超时租约、瞬时失败和进程中断。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.lowcode.publish-task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LowcodePublishTaskDispatcher {

    private final LowcodePublishTaskService taskService;
    private final LowcodePublishPostActionService actionService;
    private final LowcodeOnlinePublishWorkflowExecutor workflowExecutor;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.lowcode.publish-task.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${forge.lowcode.publish-task.scan-interval-ms:30000}")
    public void dispatch() {
        if (!actionService.supportsExecution()) {
            return;
        }
        List<AiLowcodePublishTask> candidates = taskService.findCandidates(
                LocalDateTime.now(), Math.max(1, batchSize));
        for (AiLowcodePublishTask candidate : candidates) {
            dispatch(candidate);
        }
    }

    void dispatch(AiLowcodePublishTask candidate) {
        if (!actionService.supportsExecution()) {
            return;
        }
        AiLowcodePublishTask claimed = taskService.claim(candidate, workerId, LocalDateTime.now());
        if (claimed == null) {
            return;
        }
        try {
            if (taskService.isOnlinePublishTask(claimed)) {
                LowcodeOnlinePublishWorkflowExecutor.Result result =
                        workflowExecutor.execute(claimed, false);
                taskService.markCompleted(
                        claimed, result == LowcodeOnlinePublishWorkflowExecutor.Result.SUPERSEDED);
                return;
            }
            LowcodePublishPostCommand command = taskService.restore(claimed);
            LowcodePublishPostActionService.Result result = actionService.execute(command);
            taskService.markCompleted(
                    claimed, result == LowcodePublishPostActionService.Result.SUPERSEDED);
        } catch (Exception failure) {
            try {
                taskService.markFailed(claimed, failure);
            } catch (Exception stateFailure) {
                log.error("低代码发布任务执行失败且无法更新状态: taskId={}, failureType={}",
                        claimed.getId(), safeFailureType(stateFailure));
            }
            log.warn("低代码发布任务执行失败: taskId={}, requestId={}, failureType={}",
                    claimed.getId(), claimed.getRequestId(), safeFailureType(failure));
        }
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return StringUtils.defaultIfBlank(name, "UnknownFailure");
    }
}
