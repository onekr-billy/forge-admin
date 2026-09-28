package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.enums.LowcodePublishTaskStatus;
import com.mdframe.forge.plugin.generator.mapper.LowcodePublishTaskMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 授权人工恢复低代码发布 DEAD 任务。 */
@Service
@RequiredArgsConstructor
public class LowcodePublishTaskRecoveryService {

    private final LowcodePublishTaskMapper taskMapper;
    private final LowcodePublishTaskService taskService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiLowcodePublishTask requeueDead(Long taskId, Long replayedBy, String replayReason) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(400, "发布任务ID不能为空");
        }
        Long tenantId = requireCurrentTenantId();
        Long operatorId = requireOperator(replayedBy);
        String reason = normalizeReason(replayReason);
        AiLowcodePublishTask existing = TenantContextHolder.executeIgnore(
                () -> taskMapper.selectByTaskId(tenantId, taskId));
        if (existing == null) {
            throw new BusinessException(404, "发布死信不存在或不属于当前租户");
        }
        if (!LowcodePublishTaskStatus.DEAD.matches(existing.getTaskStatus())) {
            throw new BusinessException(409, "仅允许重放 DEAD 状态的发布任务");
        }
        taskService.validateReplayable(existing);
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> taskMapper.requeueDead(
                tenantId, taskId, operatorId, reason, now));
        if (updated != 1) {
            throw new BusinessException(409, "发布死信状态已变化，请刷新后重试");
        }
        AiLowcodePublishTask replayed = TenantContextHolder.executeIgnore(
                () -> taskMapper.selectByTaskId(tenantId, taskId));
        if (replayed == null) {
            throw new BusinessException("发布死信重新入队后读取失败");
        }
        return replayed;
    }

    private Long requireCurrentTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (RuntimeException noSession) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(403, "无法确定当前租户，禁止重放发布死信");
        }
        return tenantId;
    }

    private Long requireOperator(Long replayedBy) {
        if (replayedBy == null || replayedBy <= 0) {
            throw new BusinessException(403, "无法确定发布死信重放操作人");
        }
        return replayedBy;
    }

    private String normalizeReason(String replayReason) {
        String reason = StringUtils.trimToNull(replayReason);
        if (reason == null) {
            throw new BusinessException(400, "人工重放原因不能为空");
        }
        if (reason.length() > 500) {
            throw new BusinessException(400, "人工重放原因不能超过500个字符");
        }
        return reason;
    }
}
