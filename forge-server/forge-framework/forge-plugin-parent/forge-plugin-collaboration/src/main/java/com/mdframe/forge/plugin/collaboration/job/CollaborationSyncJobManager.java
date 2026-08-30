package com.mdframe.forge.plugin.collaboration.job;

import com.mdframe.forge.plugin.job.dto.JobConfigSaveRequest;
import com.mdframe.forge.plugin.job.entity.SysJobConfig;
import com.mdframe.forge.plugin.job.service.ISysJobConfigService;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 连接维度的目录同步定时任务托管器（需求3）。
 * <p>
 * 用户在连接管理页勾选“定时同步”并填写 Cron 后，由本托管器自动创建/更新/删除该连接对应的
 * 一条 {@code COLLABORATION} 分组定时任务，用户无需再进入定时任务模块手工配置。
 * <p>
 * 每个连接对应唯一任务名 {@code collab-dir-sync-{连接ID}}；底层复用 {@link ISysJobConfigService}
 * 的增改删，天然带 Quartz 同步、乐观锁与安全校验。执行器为 {@code collaborationDirectorySync}
 * （HANDLER 模式），jobParam 为连接ID，Handler 只同步该连接。
 * <p>
 * 仅当 {@code forge-plugin-job} 在运行期存在（即部署了调度器的服务）时才装配，
 * app-server 等不启调度器的服务不会创建本 Bean。
 */
@Slf4j
@Component
@ConditionalOnClass(ISysJobConfigService.class)
@RequiredArgsConstructor
public class CollaborationSyncJobManager {

    /** 连接目录同步任务分组 */
    private static final String JOB_GROUP = "COLLABORATION";

    /** 连接目录同步任务名前缀，拼接连接ID保证全局唯一 */
    private static final String JOB_NAME_PREFIX = "collab-dir-sync-";

    /** 目录全量同步执行器编码，见 CollaborationDirectorySyncJobHandler */
    private static final String EXECUTOR_HANDLER = "collaborationDirectorySync";

    private final ISysJobConfigService jobConfigService;

    /**
     * 按连接当前配置对账定时任务：需要则新建/更新，不需要则删除，保证幂等。
     */
    public void applySchedule(SysSocialConfig connection) {
        if (connection == null || connection.getId() == null) {
            return;
        }
        String jobName = JOB_NAME_PREFIX + connection.getId();
        SysJobConfig existing = jobConfigService.findByJobKey(jobName, JOB_GROUP);
        boolean wantEnabled = EnableStatus.ENABLED.matches(connection.getSyncScheduleEnabled())
                && StringUtils.isNotBlank(connection.getSyncCron())
                && EnableStatus.ENABLED.matches(connection.getStatus());
        if (!wantEnabled) {
            if (existing != null) {
                jobConfigService.deleteJob(existing.getId());
                log.info("连接停用定时同步，已删除定时任务: jobName={}", jobName);
            }
            return;
        }
        JobConfigSaveRequest request = buildRequest(connection, jobName);
        if (existing == null) {
            jobConfigService.addJob(request);
            log.info("连接开启定时同步，已创建定时任务: jobName={}, cron={}", jobName, connection.getSyncCron());
        } else {
            request.setId(existing.getId());
            request.setVersion(existing.getVersion());
            jobConfigService.updateJob(request);
            log.info("连接更新定时同步，已更新定时任务: jobName={}, cron={}", jobName, connection.getSyncCron());
        }
    }

    /**
     * 连接删除时移除其定时任务，避免残留孤儿任务。
     */
    public void removeSchedule(Long connectionId) {
        if (connectionId == null) {
            return;
        }
        SysJobConfig existing = jobConfigService.findByJobKey(JOB_NAME_PREFIX + connectionId, JOB_GROUP);
        if (existing != null) {
            jobConfigService.deleteJob(existing.getId());
            log.info("连接已删除，同步移除定时任务: connectionId={}", connectionId);
        }
    }

    private JobConfigSaveRequest buildRequest(SysSocialConfig connection, String jobName) {
        JobConfigSaveRequest request = new JobConfigSaveRequest();
        request.setJobName(jobName);
        request.setJobGroup(JOB_GROUP);
        request.setDescription("企业协同目录定时同步 · 连接#" + connection.getId());
        request.setInvokeMode("SINGLE");
        request.setExecuteMode("HANDLER");
        request.setExecutorHandler(EXECUTOR_HANDLER);
        request.setScheduleType("CRON");
        request.setCronExpression(connection.getSyncCron());
        request.setJobParam(String.valueOf(connection.getId()));
        request.setStatus(EnableStatus.ENABLED.getCode());
        request.setConcurrentPolicy("SKIP_IF_RUNNING");
        request.setMisfirePolicy("DO_NOTHING");
        request.setIdempotentFlag(0);
        request.setRetryCount(0);
        request.setAlarmEnabled(EnableStatus.DISABLED.getCode());
        return request;
    }
}
