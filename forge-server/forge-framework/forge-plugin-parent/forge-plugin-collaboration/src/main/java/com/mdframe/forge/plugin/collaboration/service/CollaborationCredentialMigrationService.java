package com.mdframe.forge.plugin.collaboration.service;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.core.context.CryptoProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.migration.CryptoMigrationItem;
import com.mdframe.forge.starter.crypto.migration.CryptoMigrationReport;
import com.mdframe.forge.starter.social.domain.dto.SocialAppSaveCommand;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialCapabilityBinding;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.mapper.SysSocialConfigMapper;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 旧登录凭据兼容迁移服务（Task 4C）
 * <p>
 * 将 sys_social_config.client_secret 旧明文迁移为连接下 LOGIN 应用的版本化密文：
 * 批次先完整预检，再在 REQUIRES_NEW 事务内创建/复用应用并以 id+旧值 CAS 清空明文；
 * 任一 CAS 零行更新视为并发冲突，整批回滚并中止，禁止猜测归属。旧明文成功清空后不可回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationCredentialMigrationService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;
    private static final String SOURCE = "SOCIAL_CONNECTION";
    private static final String TABLE_NAME = "sys_social_config";
    private static final String FIELD_NAME = "client_secret";
    private static final String LEGACY_LOGIN_APP_CODE = "legacy_login";

    private final SysSocialConfigMapper socialConfigMapper;
    private final ISocialAppConfigService appConfigService;
    private final CryptoProperties cryptoProperties;
    private final PlatformTransactionManager transactionManager;

    /**
     * 盘点仍保存旧明文凭据的连接（只读，不修改任何数据）
     */
    public CryptoMigrationReport inventory(Long tenantId) {
        CryptoMigrationReport report = baseReport(tenantId);
        scan(tenantId, normalizeBatchSize(null), true, report);
        return report;
    }

    /**
     * 迁移旧明文凭据到 LOGIN 应用密文；dryRun 只预检不落库
     */
    public CryptoMigrationReport migrateCredentials(Long tenantId,
                                                    String expectedActiveKeyId,
                                                    Integer batchSize,
                                                    boolean dryRun) {
        assertActiveKey(expectedActiveKeyId);
        CryptoMigrationReport report = baseReport(tenantId);
        report.setExpectedActiveKeyId(expectedActiveKeyId);
        report.setBatchSize(normalizeBatchSize(batchSize));
        report.setDryRun(dryRun);
        scan(tenantId, report.getBatchSize(), dryRun, report);
        return report;
    }

    private void scan(Long tenantId, int batchSize, boolean dryRun, CryptoMigrationReport report) {
        Long afterId = null;
        while (true) {
            List<SysSocialConfig> batch = socialConfigMapper.selectLegacySecretInventory(tenantId, afterId, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            afterId = batch.get(batch.size() - 1).getId();

            // 批次完整预检：阻塞项只记录不迁移
            List<SysSocialConfig> migratable = new ArrayList<>();
            for (SysSocialConfig connection : batch) {
                String blockedReason = precheck(connection);
                if (blockedReason != null) {
                    report.addItem(CryptoMigrationItem.blocked(
                            SOURCE, identifier(connection), connection.getConnectionCode(),
                            TABLE_NAME, FIELD_NAME, blockedReason));
                    continue;
                }
                if (dryRun) {
                    report.addItem(CryptoMigrationItem.count(
                            SOURCE, identifier(connection), connection.getConnectionCode(),
                            TABLE_NAME, FIELD_NAME, "LEGACY", 1));
                    continue;
                }
                migratable.add(connection);
            }
            if (dryRun || migratable.isEmpty()) {
                continue;
            }
            if (!migrateBatch(migratable, report)) {
                // 批次冲突已整体回滚，中止后续批次，等待人工确认后重试
                return;
            }
        }
    }

    /**
     * @return true 表示批次全部成功提交；false 表示批次冲突已回滚，需中止迁移
     */
    private boolean migrateBatch(List<SysSocialConfig> migratable, CryptoMigrationReport report) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> {
                for (SysSocialConfig connection : migratable) {
                    try {
                        TenantContextHolder.executeWithTenant(connection.getTenantId(),
                                () -> migrateConnection(connection));
                    } catch (BatchAbortException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new BatchAbortException(identifier(connection), "FAILED", e.getMessage());
                    }
                }
            });
        } catch (BatchAbortException e) {
            CryptoMigrationItem item = new CryptoMigrationItem();
            item.setSource(SOURCE);
            item.setIdentifier(e.identifier());
            item.setTableName(TABLE_NAME);
            item.setFieldName(FIELD_NAME);
            item.setFormat(e.format());
            item.setCount(1L);
            item.setStatus(e.format());
            item.setReason(StrUtil.format("批次已整体回滚: {}", e.reason()));
            report.addItem(item);
            log.warn("旧凭据迁移批次回滚, identifier={}, format={}, reason={}", e.identifier(), e.format(), e.reason());
            return false;
        }
        for (SysSocialConfig connection : migratable) {
            report.addItem(CryptoMigrationItem.count(
                    SOURCE, identifier(connection), connection.getConnectionCode(),
                    TABLE_NAME, FIELD_NAME, "MIGRATED", 1));
        }
        return true;
    }

    /**
     * 单连接迁移：复用已有 LOGIN 绑定，否则创建 legacy_login 应用并绑定，最后 CAS 清空旧明文
     */
    private void migrateConnection(SysSocialConfig connection) {
        Long tenantId = connection.getTenantId();
        Long connectionId = connection.getId();
        SysSocialCapabilityBinding loginBinding = findActiveLoginBinding(tenantId, connectionId);
        if (loginBinding == null) {
            SysSocialAppConfig app = findAppByCode(tenantId, connectionId, LEGACY_LOGIN_APP_CODE);
            if (app == null) {
                SocialAppSaveCommand command = new SocialAppSaveCommand();
                command.setTenantId(tenantId);
                command.setConnectionId(connectionId);
                command.setAppCode(LEGACY_LOGIN_APP_CODE);
                command.setAppName(StrUtil.blankToDefault(connection.getConnectionName(),
                        StrUtil.blankToDefault(connection.getPlatformName(), connection.getPlatform())) + "登录应用");
                command.setClientId(connection.getClientId());
                command.setAgentId(connection.getAgentId());
                command.setRedirectUri(connection.getRedirectUri());
                command.setScope(connection.getScope());
                // 旧明文只在此处短暂传递，由应用配置服务加密落库
                command.setSecret(connection.getClientSecret());
                command.setStatus(EnableStatus.ENABLED.getCode());
                command.setRemark("旧登录凭据自动迁移生成");
                appConfigService.createApp(command);
                app = findAppByCode(tenantId, connectionId, LEGACY_LOGIN_APP_CODE);
                if (app == null) {
                    throw new BusinessException("迁移应用创建后不可见，疑似并发删除");
                }
            }
            appConfigService.bindCapability(tenantId, connectionId, CollaborationCapability.LOGIN, app.getId());
        }
        int rows = socialConfigMapper.clearLegacySecretCas(connectionId, connection.getClientSecret());
        if (rows == 0) {
            throw new BatchAbortException(identifier(connection), "CONFLICT", "旧明文已被并发修改");
        }
    }

    /**
     * @return 阻塞原因，null 表示可迁移
     */
    private String precheck(SysSocialConfig connection) {
        if (connection.getTenantId() == null) {
            return "连接缺少租户，禁止猜测归属";
        }
        if (StrUtil.isBlank(connection.getClientId())) {
            return "连接缺少clientId，无法生成登录应用";
        }
        return null;
    }

    private SysSocialCapabilityBinding findActiveLoginBinding(Long tenantId, Long connectionId) {
        return appConfigService.listBindings(tenantId, connectionId).stream()
                .filter(binding -> CollaborationCapability.LOGIN.name().equals(binding.getCapability()))
                .filter(binding -> EnableStatus.ENABLED.matches(binding.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private SysSocialAppConfig findAppByCode(Long tenantId, Long connectionId, String appCode) {
        return appConfigService.listApps(tenantId, connectionId).stream()
                .filter(app -> appCode.equals(app.getAppCode()))
                .findFirst()
                .orElse(null);
    }

    private String identifier(SysSocialConfig connection) {
        if (StrUtil.isNotBlank(connection.getConnectionCode())) {
            return connection.getConnectionCode();
        }
        return "connection-" + connection.getId();
    }

    private CryptoMigrationReport baseReport(Long tenantId) {
        CryptoMigrationReport report = CryptoMigrationReport.of(tenantId, SOURCE);
        report.setActiveKeyId(activeKeyId());
        report.setDryRun(true);
        return report;
    }

    private void assertActiveKey(String expectedActiveKeyId) {
        CryptoProperties.PersistenceProperties persistence = cryptoProperties.getPersistence();
        if (persistence == null
                || !Boolean.TRUE.equals(persistence.getEnabled())
                || !Boolean.TRUE.equals(persistence.getWriteVersioned())) {
            throw new BusinessException("执行凭据迁移前必须启用版本化写入");
        }
        if (StringUtils.isBlank(expectedActiveKeyId)) {
            throw new BusinessException("expectedActiveKeyId不能为空");
        }
        if (!StringUtils.equals(expectedActiveKeyId, persistence.getActiveKeyId())) {
            throw new BusinessException("expectedActiveKeyId与当前活动keyId不一致");
        }
    }

    private String activeKeyId() {
        CryptoProperties.PersistenceProperties persistence = cryptoProperties.getPersistence();
        return persistence == null ? null : persistence.getActiveKeyId();
    }

    private int normalizeBatchSize(Integer batchSize) {
        int value = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        return Math.min(value, MAX_BATCH_SIZE);
    }

    /**
     * 批次中止信号：携带定位标识与冲突类型，触发整批回滚
     */
    private static final class BatchAbortException extends RuntimeException {

        private final String identifier;
        private final String format;
        private final String reason;

        private BatchAbortException(String identifier, String format, String reason) {
            super("credential migration batch abort");
            this.identifier = identifier;
            this.format = format;
            this.reason = reason;
        }

        private String identifier() {
            return identifier;
        }

        private String format() {
            return format;
        }

        private String reason() {
            return reason;
        }
    }
}
