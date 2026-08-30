package com.mdframe.forge.plugin.collaboration.job;

import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncCommand;
import com.mdframe.forge.plugin.collaboration.service.directory.DirectorySyncOrchestrator;
import com.mdframe.forge.plugin.job.executor.IJobExecutor;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.job.annotation.JobHandler;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业协同目录定时全量同步 Handler（Task 11）。
 * <p>
 * 定时全量修复回调丢事件：遍历启用的协同连接逐个触发全量同步；
 * 同连接并发由编排器的分布式锁 + RUNNING 批次互斥保证，此处被拒绝直接跳过。
 * 参数为空同步全部连接，否则按逗号分隔的连接 ID 列表执行。
 */
@Slf4j
@Component
@ConditionalOnClass(IJobExecutor.class)
@IgnoreTenant
@RequiredArgsConstructor
@JobHandler(value = "collaborationDirectorySync", description = "企业协同目录全量同步", group = "COLLABORATION")
public class CollaborationDirectorySyncJobHandler implements IJobExecutor {

    private static final String CONNECTION_TYPE_OAUTH_ONLY = "OAUTH_ONLY";

    private final ISocialConfigService configService;
    private final DirectorySyncOrchestrator syncOrchestrator;

    @Override
    public String execute(String param) {
        List<SysSocialConfig> connections = resolveConnections(param);
        if (connections.isEmpty()) {
            return "无可同步的协同连接";
        }
        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (SysSocialConfig connection : connections) {
            try {
                syncOrchestrator.synchronize(connection.getId(), new DirectorySyncCommand(
                        "FULL", "JOB", DirectorySyncScope.FULL, null, null));
                success++;
            } catch (BusinessException e) {
                // 并发互斥/能力不支持等业务性拒绝，跳过继续下一个连接
                skipped++;
                log.warn("连接目录同步跳过: connectionId={}, reason={}", connection.getId(), e.getMessage());
            } catch (Exception e) {
                failed++;
                log.error("连接目录同步异常: connectionId={}", connection.getId(), e);
            }
        }
        String summary = String.format("目录同步完成: total=%d, success=%d, skipped=%d, failed=%d",
                connections.size(), success, skipped, failed);
        log.info(summary);
        return summary;
    }

    /**
     * 解析目标连接：参数为逗号分隔连接 ID，空则取全部启用且非仅登录的连接
     */
    private List<SysSocialConfig> resolveConnections(String param) {
        if (StringUtils.hasText(param)) {
            List<SysSocialConfig> targets = new ArrayList<>();
            for (String part : param.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                SysSocialConfig connection = configService.selectConfigById(Long.parseLong(trimmed));
                if (connection != null) {
                    targets.add(connection);
                } else {
                    log.warn("目录同步参数中的连接不存在: {}", trimmed);
                }
            }
            return targets;
        }
        SysSocialConfig query = new SysSocialConfig();
        query.setStatus(com.mdframe.forge.starter.core.enums.EnableStatus.ENABLED.getCode());
        return configService.selectConfigList(query).stream()
                .filter(c -> !CONNECTION_TYPE_OAUTH_ONLY.equals(c.getConnectionType()))
                .toList();
    }
}
