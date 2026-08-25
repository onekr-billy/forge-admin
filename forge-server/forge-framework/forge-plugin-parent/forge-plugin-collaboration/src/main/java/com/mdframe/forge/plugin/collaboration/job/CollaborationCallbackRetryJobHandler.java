package com.mdframe.forge.plugin.collaboration.job;

import com.mdframe.forge.plugin.collaboration.service.directory.DirectoryCallbackEventProcessor;
import com.mdframe.forge.plugin.job.executor.IJobExecutor;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.job.annotation.JobHandler;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 企业协同回调收件箱补偿 Handler（Task 11）。
 * <p>
 * 周期性领取各租户 PENDING / 到期 FAILED 的回调事件并交由
 * {@link DirectoryCallbackEventProcessor} 归并处理；收件箱 CAS 领取保证
 * 多实例并发下同一事件只被一个 Worker 消费。参数为单租户领取批次大小，空取默认值。
 */
@Slf4j
@Component
@ConditionalOnClass(IJobExecutor.class)
@IgnoreTenant
@RequiredArgsConstructor
@JobHandler(value = "collaborationCallbackRetry", description = "企业协同回调收件箱补偿", group = "COLLABORATION")
public class CollaborationCallbackRetryJobHandler implements IJobExecutor {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final ISocialConfigService configService;
    private final DirectoryCallbackEventProcessor eventProcessor;

    @Override
    public String execute(String param) {
        int batchSize = parseBatchSize(param);
        Set<Long> tenantIds = resolveTenantIds();
        if (tenantIds.isEmpty()) {
            return "无启用的协同连接，跳过回调补偿";
        }
        String workerId = buildWorkerId();
        int claimed = 0;
        int processed = 0;
        int failed = 0;
        for (Long tenantId : tenantIds) {
            try {
                DirectoryCallbackEventProcessor.ProcessResult result =
                        eventProcessor.processTenant(tenantId, batchSize, workerId);
                claimed += result.claimed();
                processed += result.processed();
                failed += result.failed();
            } catch (Exception e) {
                log.error("租户回调事件补偿异常: tenantId={}", tenantId, e);
            }
        }
        String summary = String.format("回调补偿完成: tenants=%d, claimed=%d, processed=%d, failed=%d",
                tenantIds.size(), claimed, processed, failed);
        log.info(summary);
        return summary;
    }

    private Set<Long> resolveTenantIds() {
        SysSocialConfig query = new SysSocialConfig();
        query.setStatus(com.mdframe.forge.starter.core.enums.EnableStatus.ENABLED.getCode());
        Set<Long> tenantIds = new LinkedHashSet<>();
        for (SysSocialConfig connection : configService.selectConfigList(query)) {
            if (connection.getTenantId() != null) {
                tenantIds.add(connection.getTenantId());
            }
        }
        return tenantIds;
    }

    private int parseBatchSize(String param) {
        if (!StringUtils.hasText(param)) {
            return DEFAULT_BATCH_SIZE;
        }
        try {
            int size = Integer.parseInt(param.trim());
            return size > 0 ? size : DEFAULT_BATCH_SIZE;
        } catch (NumberFormatException e) {
            log.warn("回调补偿参数无效，使用默认批次大小: {}", param);
            return DEFAULT_BATCH_SIZE;
        }
    }

    /**
     * Worker 标识：主机名 + 随机后缀，保证多实例与多轮执行互不认领对方事件
     */
    private String buildWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "worker";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
