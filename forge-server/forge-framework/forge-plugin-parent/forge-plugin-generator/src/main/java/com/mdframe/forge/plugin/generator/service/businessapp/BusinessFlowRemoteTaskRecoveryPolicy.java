package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

/** 约束交互式任务命令恢复只能由原操作者以完全相同的远端动作身份触发。 */
final class BusinessFlowRemoteTaskRecoveryPolicy {

    private BusinessFlowRemoteTaskRecoveryPolicy() {
    }

    static void validateInteractive(
            BusinessFlowRemoteTaskRequest request,
            Long expectedUserId,
            String expectedCommandType,
            String expectedTaskId,
            BusinessFlowCommandIdentity.Credentials expectedCredentials,
            String expectedSubmissionDigest) {
        if (expectedUserId == null) {
            return;
        }
        if (!expectedUserId.equals(request.getOperatorUserId())) {
            throw new BusinessException(403, "FLOW_TASK_RECOVERY_OPERATOR_MISMATCH");
        }
        if (!StringUtils.equals(expectedCommandType, request.getCommandType())
                || !StringUtils.equals(StringUtils.trimToNull(expectedTaskId), request.getTaskId())
                || expectedCredentials == null
                || !StringUtils.equals(expectedCredentials.idempotencyKey(), request.getIdempotencyKey())
                || !StringUtils.equals(expectedCredentials.requestDigest(), request.getActionRequestDigest())
                || (expectedSubmissionDigest != null
                    && StringUtils.isNotBlank(request.getSubmissionDigest())
                    && !StringUtils.equals(expectedSubmissionDigest, request.getSubmissionDigest()))) {
            throw new BusinessException(409, "FLOW_TASK_RECOVERY_IDENTITY_MISMATCH");
        }
    }
}
