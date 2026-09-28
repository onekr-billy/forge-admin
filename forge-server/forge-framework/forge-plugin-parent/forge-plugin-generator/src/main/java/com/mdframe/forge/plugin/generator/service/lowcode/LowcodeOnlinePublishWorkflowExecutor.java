package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 按持久化阶段恢复在线 DDL、配置版本和发布后置同步。 */
@Service
@RequiredArgsConstructor
public class LowcodeOnlinePublishWorkflowExecutor {

    private final LowcodePublishTaskService taskService;
    private final LowcodeDdlService ddlService;
    private final LowcodeOnlinePublishConfigActionService configActionService;
    private final LowcodePublishPostActionService postActionService;
    private final ObjectMapper objectMapper;

    public Result execute(AiLowcodePublishTask task, boolean deferPostSync) {
        LowcodeOnlinePublishCommand command = taskService.restoreOnlinePublish(task);
        return TenantContextHolder.executeWithTenant(
                command.tenantId(), () -> executeInTenant(task, command, deferPostSync));
    }

    private Result executeInTenant(AiLowcodePublishTask task,
                                   LowcodeOnlinePublishCommand command,
                                   boolean deferPostSync) {
        while (true) {
            String stage = task.getCurrentStage();
            if (LowcodePublishTaskService.STAGE_DDL_PENDING.equals(stage)) {
                ddlService.executeCreateTableWithoutTransaction(readModelSchema(command));
                taskService.advanceStage(task, stage, LowcodePublishTaskService.STAGE_CONFIG_PENDING);
                continue;
            }
            if (LowcodePublishTaskService.STAGE_CONFIG_PENDING.equals(stage)) {
                LowcodeOnlinePublishConfigActionService.Result configResult =
                        configActionService.execute(command);
                if (configResult == LowcodeOnlinePublishConfigActionService.Result.SUPERSEDED) {
                    return Result.SUPERSEDED;
                }
                if (deferPostSync) {
                    taskService.releaseStage(
                            task, stage, LowcodePublishTaskService.STAGE_POST_SYNC);
                    return Result.DEFERRED;
                }
                taskService.advanceStage(task, stage, LowcodePublishTaskService.STAGE_POST_SYNC);
                continue;
            }
            if (LowcodePublishTaskService.STAGE_POST_SYNC.equals(stage)) {
                LowcodePublishPostActionService.Result result =
                        postActionService.execute(command.toPostCommand());
                return result == LowcodePublishPostActionService.Result.SUPERSEDED
                        ? Result.SUPERSEDED : Result.COMPLETED;
            }
            throw new BusinessException("在线发布任务阶段不支持: " + stage);
        }
    }

    private LowcodeModelSchema readModelSchema(LowcodeOnlinePublishCommand command) {
        try {
            return objectMapper.readValue(
                    command.configSnapshot().getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception invalidSnapshot) {
            throw new BusinessException("在线发布模型快照格式无效");
        }
    }

    public enum Result {
        COMPLETED,
        SUPERSEDED,
        DEFERRED
    }
}
