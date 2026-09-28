package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 在线 DDL 成功后，以独立事务提交配置和不可变发布版本。 */
@Service
@RequiredArgsConstructor
public class LowcodeOnlinePublishConfigActionService {

    private static final String GENERAL_DOMAIN_CODE = "general";
    private static final String MOUNT_ADMIN = "ADMIN";
    private static final String MOUNT_BOTH = "BOTH";

    private final AiCrudConfigMapper configMapper;
    private final AiCrudConfigVersionMapper versionMapper;
    private final LowcodeDomainService domainService;
    private final MenuRegisterAdapter menuRegisterAdapter;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Result execute(LowcodeOnlinePublishCommand command) {
        validateCommand(command);
        return TenantContextHolder.executeWithTenant(
                command.tenantId(), () -> executeInTenant(command));
    }

    private Result executeInTenant(LowcodeOnlinePublishCommand command) {
        AiCrudConfig config = configMapper.selectByConfigIdForUpdate(
                command.tenantId(), command.configId());
        if (config == null || !Objects.equals(command.tenantId(), config.getTenantId())
                || !Objects.equals(command.configKey(), config.getConfigKey())) {
            return Result.SUPERSEDED;
        }
        if (Objects.equals(command.versionNo(), config.getPublishedVersion())) {
            return resolvePublishedVersion(command);
        }
        if (!Objects.equals(command.expectedDraftVersion(), config.getDraftVersion())
                || !Objects.equals(command.expectedPublishedVersion(), config.getPublishedVersion())) {
            return Result.SUPERSEDED;
        }

        LowcodeOnlinePublishConfigSnapshot snapshot = command.configSnapshot();
        snapshot.applyTo(config);
        resolveMenuParent(config, command);
        LocalDateTime now = LocalDateTime.now();
        config.setPublishStatus("PUBLISHED");
        config.setPublishedVersion(command.versionNo());
        config.setPublishTime(now);
        config.setPublishBy(command.operatorId());
        config.setUpdateBy(command.operatorId());

        AiCrudConfigVersion version = buildVersion(command, config, now);
        if (versionMapper.insert(version) != 1) {
            throw new BusinessException("在线发布版本持久化失败");
        }
        if (configMapper.updateById(config) != 1) {
            throw new BusinessException("在线发布配置持久化失败");
        }
        return Result.COMPLETED;
    }

    private Result resolvePublishedVersion(LowcodeOnlinePublishCommand command) {
        AiCrudConfigVersion existing = versionMapper.selectVersionById(
                command.tenantId(), command.configId(), command.versionId());
        if (existing != null) {
            if (!Objects.equals(command.configKey(), existing.getConfigKey())
                    || !Objects.equals(command.versionNo(), existing.getVersionNo())) {
                throw new BusinessException("在线发布配置与版本提交身份不一致");
            }
            return Result.COMPLETED;
        }
        AiCrudConfigVersion winningVersion = versionMapper.selectVersionByNo(
                command.tenantId(), command.configId(), command.versionNo());
        if (winningVersion != null) {
            return Result.SUPERSEDED;
        }
        throw new BusinessException("在线发布配置与版本提交状态不一致");
    }

    private AiCrudConfigVersion buildVersion(LowcodeOnlinePublishCommand command,
                                             AiCrudConfig config,
                                             LocalDateTime now) {
        LowcodeOnlinePublishConfigSnapshot snapshot = command.configSnapshot();
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setId(command.versionId());
        version.setTenantId(command.tenantId());
        version.setConfigId(command.configId());
        version.setConfigKey(command.configKey());
        version.setDomainId(snapshot.getDomainId());
        version.setDomainCode(snapshot.getDomainCode());
        version.setObjectCode(snapshot.getObjectCode());
        version.setObjectName(snapshot.getObjectName());
        version.setVersionNo(command.versionNo());
        version.setVersionType("publish");
        version.setModelSchema(snapshot.getModelSchema());
        version.setPageSchema(snapshot.getPageSchema());
        version.setSearchSchema(snapshot.getSearchSchema());
        version.setColumnsSchema(snapshot.getColumnsSchema());
        version.setEditSchema(snapshot.getEditSchema());
        version.setApiConfig(snapshot.getApiConfig());
        version.setOptions(snapshot.getOptions());
        version.setRuntimeDatasourceId(snapshot.getRuntimeDatasourceId());
        version.setRuntimeDatasourceCode(snapshot.getRuntimeDatasourceCode());
        version.setRuntimeDatasourceSnapshot(snapshot.getRuntimeDatasourceSnapshot());
        version.setRuntimeTableName(snapshot.getRuntimeTableName());
        version.setPrimaryKeyField(snapshot.getPrimaryKeyField());
        version.setPrimaryKeyColumn(snapshot.getPrimaryKeyColumn());
        version.setPrimaryKeyType(snapshot.getPrimaryKeyType());
        version.setTenantStrategy(snapshot.getTenantStrategy());
        version.setAuditStrategy(snapshot.getAuditStrategy());
        version.setLogicDeleteStrategy(snapshot.getLogicDeleteStrategy());
        version.setPublishSnapshot(snapshot.toPublishSnapshotJson(objectMapper, config.getConfigKey()));
        version.setRemark(StringUtils.defaultIfBlank(command.remark(), "发布低代码应用"));
        version.setCreateBy(command.operatorId());
        version.setUpdateBy(command.operatorId());
        version.setCreateTime(now);
        version.setUpdateTime(now);
        return version;
    }

    private void resolveMenuParent(AiCrudConfig config, LowcodeOnlinePublishCommand command) {
        if (!Boolean.TRUE.equals(command.syncMenu()) || !shouldMountAdmin(config.getMountTarget())) {
            return;
        }
        AiLowcodeDomain domain = resolveDomain(
                config.getDomainId(), config.getDomainCode());
        Long domainParentId = resolveDomainMenuParentId(domain, new HashSet<>());
        Long requestedParentId = command.requestedMenuParentId();
        config.setMenuParentId(requestedParentId != null && !requestedParentId.equals(domainParentId)
                ? requestedParentId
                : domainParentId);
        if (config.getMenuParentId() == null) {
            config.setMenuParentId(menuRegisterAdapter.resolveDefaultLowcodeParentId());
        }
    }

    private AiLowcodeDomain resolveDomain(Long domainId, String domainCode) {
        if (domainId != null) {
            try {
                return domainService.requireDomain(domainId);
            } catch (BusinessException ignored) {
                // 继续按编码和通用业务域恢复不可变发布计划。
            }
        }
        if (StringUtils.isNotBlank(domainCode)) {
            AiLowcodeDomain domain = domainService.getByCode(domainCode);
            if (domain != null) {
                return domain;
            }
        }
        AiLowcodeDomain generalDomain = domainService.getByCode(GENERAL_DOMAIN_CODE);
        if (generalDomain == null) {
            throw new BusinessException("通用业务域不存在，无法恢复在线发布");
        }
        return generalDomain;
    }

    private Long resolveDomainMenuParentId(AiLowcodeDomain domain, Set<Long> resolvingDomainIds) {
        if (domain == null) {
            return null;
        }
        Long domainId = domain.getId();
        if (domainId != null && !resolvingDomainIds.add(domainId)) {
            throw new BusinessException("业务领域层级存在循环引用，请先调整领域父级");
        }
        try {
            Long parentMenuId = menuRegisterAdapter.resolveDefaultLowcodeParentId();
            if (domain.getParentId() != null && domain.getParentId() > 0) {
                parentMenuId = resolveDomainMenuParentId(
                        domainService.requireDomain(domain.getParentId()), resolvingDomainIds);
            }
            return menuRegisterAdapter.resolveOrCreateDomainParentId(
                    domain.getDomainCode(), domain.getDomainName(), domain.getSort(), parentMenuId);
        } finally {
            if (domainId != null) {
                resolvingDomainIds.remove(domainId);
            }
        }
    }

    private boolean shouldMountAdmin(String mountTarget) {
        return MOUNT_ADMIN.equalsIgnoreCase(mountTarget) || MOUNT_BOTH.equalsIgnoreCase(mountTarget);
    }

    private void validateCommand(LowcodeOnlinePublishCommand command) {
        if (command == null || command.protocolVersion() == null
                || command.protocolVersion() != LowcodePublishTaskService.COMMAND_PROTOCOL_VERSION
                || command.tenantId() == null || command.tenantId() <= 0
                || command.configId() == null || StringUtils.isBlank(command.configKey())
                || command.expectedDraftVersion() == null || command.expectedDraftVersion() < 0
                || command.expectedPublishedVersion() == null || command.expectedPublishedVersion() < 0
                || command.versionId() == null || command.versionNo() == null
                || command.versionNo() <= command.expectedPublishedVersion()
                || command.operatorId() == null || command.operatorId() <= 0
                || command.configSnapshot() == null) {
            throw new BusinessException("在线发布配置命令身份无效");
        }
    }

    public enum Result {
        COMPLETED,
        SUPERSEDED
    }
}
