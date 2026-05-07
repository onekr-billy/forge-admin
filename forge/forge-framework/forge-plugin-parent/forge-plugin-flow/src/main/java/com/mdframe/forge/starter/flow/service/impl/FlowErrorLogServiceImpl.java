package com.mdframe.forge.starter.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;
import com.mdframe.forge.starter.flow.mapper.FlowErrorLogMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程运行错误日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowErrorLogServiceImpl extends ServiceImpl<FlowErrorLogMapper, FlowErrorLog> implements FlowErrorLogService {

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int DEFAULT_JOB_RETRIES = 3;

    private final RuntimeService runtimeService;
    private final ManagementService managementService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordError(FlowErrorLog errorLog, Throwable throwable) {
        if (errorLog == null) {
            errorLog = new FlowErrorLog();
        }
        if (throwable != null) {
            errorLog.setErrorType(throwable.getClass().getName());
            if (errorLog.getErrorMessage() == null || errorLog.getErrorMessage().isEmpty()) {
                errorLog.setErrorMessage(truncate(throwable.getMessage(), MAX_TEXT_LENGTH));
            }
            errorLog.setStackTrace(truncate(getStackTrace(throwable), MAX_TEXT_LENGTH));
        }
        if (errorLog.getErrorMessage() == null || errorLog.getErrorMessage().isEmpty()) {
            errorLog.setErrorMessage("流程运行异常");
        }
        if (errorLog.getErrorType() == null || errorLog.getErrorType().isEmpty()) {
            errorLog.setErrorType("FLOW_RUNTIME_ERROR");
        }
        if (errorLog.getStatus() == null) {
            errorLog.setStatus(0);
        }
        if (errorLog.getRetryCount() == null) {
            errorLog.setRetryCount(0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (errorLog.getCreateTime() == null) {
            errorLog.setCreateTime(now);
        }
        errorLog.setUpdateTime(now);
        save(errorLog);
    }

    @Override
    public IPage<FlowErrorLog> pageErrors(Page<FlowErrorLog> page, String processInstanceId, String activityId, Integer status) {
        return baseMapper.selectErrorLogPage(page, processInstanceId, activityId, status);
    }

    @Override
    public List<FlowErrorLog> listRecentByProcessInstanceId(String processInstanceId) {
        return baseMapper.selectRecentByProcessInstanceId(processInstanceId);
    }

    @Override
    public Long countUnresolvedByProcessInstanceId(String processInstanceId) {
        Long count = baseMapper.countUnresolvedByProcessInstanceId(processInstanceId);
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryNode(String processInstanceId, String activityId, String logId, String userId, String reason) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            throw new RuntimeException("流程实例ID不能为空");
        }

        FlowErrorLog errorLog = findRetryLog(processInstanceId, activityId, logId);
        String retryActivityId = activityId;
        if ((retryActivityId == null || retryActivityId.isEmpty()) && errorLog != null) {
            retryActivityId = errorLog.getActivityId();
        }

        try {
            boolean retried = retryFlowableJob(processInstanceId, retryActivityId, errorLog);
            if (!retried) {
                retryActiveActivity(processInstanceId, retryActivityId);
            }
            markRetrySuccess(errorLog, userId, reason);
            log.info("流程节点重试成功：processInstanceId={}, activityId={}, logId={}, userId={}",
                    processInstanceId, retryActivityId, logId, userId);
        } catch (Exception e) {
            markRetryFailed(errorLog, userId, e.getMessage());
            FlowErrorLog retryError = new FlowErrorLog();
            retryError.setProcessInstanceId(processInstanceId);
            retryError.setActivityId(retryActivityId);
            retryError.setErrorStage("NODE_RETRY");
            retryError.setErrorMessage("流程节点重试失败：" + e.getMessage());
            recordError(retryError, e);
            throw new RuntimeException("流程节点重试失败：" + e.getMessage(), e);
        }
    }

    private FlowErrorLog findRetryLog(String processInstanceId, String activityId, String logId) {
        if (logId != null && !logId.isEmpty()) {
            FlowErrorLog errorLog = getById(logId);
            if (errorLog == null) {
                throw new RuntimeException("错误日志不存在：" + logId);
            }
            if (!processInstanceId.equals(errorLog.getProcessInstanceId())) {
                throw new RuntimeException("错误日志不属于当前流程实例");
            }
            return errorLog;
        }
        return baseMapper.selectLatestUnresolved(processInstanceId, activityId);
    }

    private boolean retryFlowableJob(String processInstanceId, String activityId, FlowErrorLog errorLog) {
        Job deadLetterJob = null;
        if (errorLog != null && errorLog.getJobId() != null && !errorLog.getJobId().isEmpty()) {
            deadLetterJob = managementService.createDeadLetterJobQuery().jobId(errorLog.getJobId()).singleResult();
        }
        if (deadLetterJob == null) {
            deadLetterJob = createDeadLetterQuery(processInstanceId, activityId).singleResult();
        }
        if (deadLetterJob != null) {
            Job executableJob = managementService.moveDeadLetterJobToExecutableJob(deadLetterJob.getId(), DEFAULT_JOB_RETRIES);
            managementService.executeJob(executableJob.getId());
            return true;
        }

        Job executableJob = createExecutableJobQuery(processInstanceId, activityId).singleResult();
        if (executableJob != null) {
            managementService.setJobRetries(executableJob.getId(), DEFAULT_JOB_RETRIES);
            managementService.executeJob(executableJob.getId());
            return true;
        }

        return false;
    }

    private org.flowable.job.api.DeadLetterJobQuery createDeadLetterQuery(String processInstanceId, String activityId) {
        org.flowable.job.api.DeadLetterJobQuery query = managementService.createDeadLetterJobQuery()
                .processInstanceId(processInstanceId);
        if (activityId != null && !activityId.isEmpty()) {
            query.elementId(activityId);
        }
        return query;
    }

    private org.flowable.job.api.JobQuery createExecutableJobQuery(String processInstanceId, String activityId) {
        org.flowable.job.api.JobQuery query = managementService.createJobQuery()
                .processInstanceId(processInstanceId)
                .withException();
        if (activityId != null && !activityId.isEmpty()) {
            query.elementId(activityId);
        }
        return query;
    }

    private void retryActiveActivity(String processInstanceId, String activityId) {
        if (activityId == null || activityId.isEmpty()) {
            throw new RuntimeException("节点ID不能为空");
        }
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在或已结束，无法重试");
        }
        if (processInstance.isSuspended()) {
            runtimeService.activateProcessInstanceById(processInstanceId);
        }
        List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        if (activeActivityIds == null || !activeActivityIds.contains(activityId)) {
            throw new RuntimeException("节点不是当前活动节点，且未找到可重试的失败作业");
        }
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(activityId, activityId)
                .changeState();
    }

    private void markRetrySuccess(FlowErrorLog errorLog, String userId, String reason) {
        if (errorLog == null) {
            return;
        }
        errorLog.setStatus(1);
        errorLog.setRetryCount(errorLog.getRetryCount() == null ? 1 : errorLog.getRetryCount() + 1);
        errorLog.setLastRetryUserId(userId);
        errorLog.setLastRetryTime(LocalDateTime.now());
        errorLog.setRetryMessage(truncate(reason, MAX_TEXT_LENGTH));
        errorLog.setUpdateTime(LocalDateTime.now());
        updateById(errorLog);
    }

    private void markRetryFailed(FlowErrorLog errorLog, String userId, String message) {
        if (errorLog == null) {
            return;
        }
        errorLog.setStatus(3);
        errorLog.setRetryCount(errorLog.getRetryCount() == null ? 1 : errorLog.getRetryCount() + 1);
        errorLog.setLastRetryUserId(userId);
        errorLog.setLastRetryTime(LocalDateTime.now());
        errorLog.setRetryMessage(truncate(message, MAX_TEXT_LENGTH));
        errorLog.setUpdateTime(LocalDateTime.now());
        updateById(errorLog);
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
