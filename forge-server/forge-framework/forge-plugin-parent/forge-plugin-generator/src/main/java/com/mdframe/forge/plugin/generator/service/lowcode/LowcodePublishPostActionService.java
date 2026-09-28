package com.mdframe.forge.plugin.generator.service.lowcode;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeModel;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/** 在独立本地事务中幂等同步发布菜单和业务应用入口。 */
@Service
@RequiredArgsConstructor
public class LowcodePublishPostActionService {

    private static final String MOUNT_ADMIN = "ADMIN";
    private static final String MOUNT_MOBILE = "MOBILE";
    private static final String MOUNT_BOTH = "BOTH";

    private final AiCrudConfigMapper configMapper;
    private final AiCrudConfigVersionMapper versionMapper;
    private final AiCrudConfigService configService;
    private final MenuRegisterAdapter menuRegisterAdapter;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessAppMapper businessAppMapper;
    private final AiLowcodeModelMapper lowcodeModelMapper;

    public boolean supportsExecution() {
        return menuRegisterAdapter.supportsLowcodePublishSynchronization();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Result execute(LowcodePublishPostCommand command) {
        validateCommand(command);
        return TenantContextHolder.executeWithTenant(command.tenantId(), () -> executeInTenant(command));
    }

    private Result executeInTenant(LowcodePublishPostCommand command) {
        AiCrudConfig config = configMapper.selectByConfigId(command.tenantId(), command.configId());
        if (config == null
                || !Objects.equals(command.tenantId(), config.getTenantId())
                || !Objects.equals(command.configKey(), config.getConfigKey())
                || !Objects.equals(command.versionNo(), config.getPublishedVersion())
                || !"PUBLISHED".equals(config.getPublishStatus())) {
            return Result.SUPERSEDED;
        }
        PublishedSnapshot snapshot = loadPublishedSnapshot(command);
        registerOrUpdateMenu(
                config, snapshot, Boolean.TRUE.equals(command.syncMenu()), command.menuParentId());
        syncBusinessRuntimeEntry(config, command, snapshot);
        if (!configService.updateById(config)) {
            throw new BusinessException("低代码发布后置配置回写失败");
        }
        return Result.COMPLETED;
    }

    private void registerOrUpdateMenu(AiCrudConfig config,
                                      PublishedSnapshot snapshot,
                                      boolean syncMenu,
                                      Long resolvedParentId) {
        String mountTarget = snapshot.mountTarget();
        boolean mountAdmin = shouldMountAdmin(mountTarget);
        boolean mountMobile = shouldMountMobile(mountTarget);
        String menuName = snapshot.menuName();
        Integer sort = snapshot.menuSort();

        if (!syncMenu) {
            disableExistingMenus(config);
            return;
        }
        if (mountAdmin) {
            Long parentId = resolvedParentId != null
                    ? resolvedParentId
                    : config.getMenuParentId() != null
                            ? config.getMenuParentId()
                            : menuRegisterAdapter.resolveDefaultLowcodeParentId();
            if (config.getMenuResourceId() == null) {
                config.setMenuResourceId(requireResourceIdentity(
                        menuRegisterAdapter.registerMenu(
                                menuName, parentId, config.getConfigKey(), sort),
                        "管理端菜单"));
            } else {
                menuRegisterAdapter.updateMenu(config.getMenuResourceId(), menuName, parentId, sort);
            }
            config.setMenuName(menuName);
            config.setMenuParentId(parentId);
            config.setMenuSort(sort);
        } else if (config.getMenuResourceId() != null) {
            menuRegisterAdapter.disableMenu(config.getMenuResourceId());
        }

        Long mobileResourceId = readMobileMenuResourceId(config);
        if (mountMobile) {
            String mobilePath = "/pages/lowcode-runtime?configKey=" + config.getConfigKey();
            String mobilePerms = "ai:crud:h5:" + config.getConfigKey();
            if (mobileResourceId == null) {
                writeMobileMenuResourceId(config, requireResourceIdentity(
                        menuRegisterAdapter.registerAppMenu(
                                menuName, 0L, mobilePath, mobilePath,
                                mobilePerms, null, sort, true, "h5"),
                        "移动端菜单"));
            } else {
                menuRegisterAdapter.updateAppMenu(
                        mobileResourceId, menuName, 0L, mobilePath, mobilePath,
                        mobilePerms, null, sort, true, "h5");
            }
        } else if (mobileResourceId != null) {
            menuRegisterAdapter.disableMenu(mobileResourceId);
        }
    }

    private void disableExistingMenus(AiCrudConfig config) {
        if (config.getMenuResourceId() != null) {
            menuRegisterAdapter.disableMenu(config.getMenuResourceId());
        }
        Long mobileResourceId = readMobileMenuResourceId(config);
        if (mobileResourceId != null) {
            menuRegisterAdapter.disableMenu(mobileResourceId);
        }
    }

    private void syncBusinessRuntimeEntry(AiCrudConfig config,
                                          LowcodePublishPostCommand command,
                                          PublishedSnapshot snapshot) {
        AiBusinessApp existingApp = businessAppMapper.selectByConfigKey(
                command.tenantId(), config.getConfigKey());
        String suiteCode = StringUtils.firstNonBlank(
                command.businessSuiteCode(),
                existingApp == null ? null : existingApp.getSuiteCode(),
                snapshot.domainCode());
        String businessObjectCode = StringUtils.firstNonBlank(
                command.businessObjectCode(),
                existingApp == null ? null : existingApp.getObjectCode(),
                snapshot.objectCode(),
                config.getConfigKey());
        if (StringUtils.isBlank(suiteCode) || StringUtils.isBlank(businessObjectCode)) {
            return;
        }
        AiBusinessObject businessObject = businessObjectMapper.selectByObjectCode(
                command.tenantId(), suiteCode, businessObjectCode);
        if (businessObject == null) {
            return;
        }

        AiLowcodeModel model = StringUtils.isBlank(snapshot.objectCode())
                ? null
                : lowcodeModelMapper.selectByCode(
                        command.tenantId(), snapshot.domainId(), snapshot.objectCode());
        Long resolvedModelId = model == null ? businessObject.getModelId() : model.getId();
        String resolvedModelCode = StringUtils.defaultIfBlank(
                snapshot.objectCode(), businessObject.getModelCode());
        if (!Objects.equals(businessObject.getModelId(), resolvedModelId)
                || !Objects.equals(businessObject.getModelCode(), resolvedModelCode)) {
            businessObject.setModelId(resolvedModelId);
            businessObject.setModelCode(resolvedModelCode);
            requireSingleWrite(businessObjectMapper.updateById(businessObject), "业务对象");
        }

        AiBusinessApp app = existingApp;
        if (app == null) {
            app = businessAppMapper.selectRuntimeAppByObject(
                    command.tenantId(), suiteCode, businessObject.getObjectCode());
        }
        boolean create = app == null;
        if (create) {
            app = new AiBusinessApp();
            app.setTenantId(command.tenantId());
            app.setAppCode(resolveRuntimeAppCode(
                    command.tenantId(), suiteCode, businessObject.getObjectCode(), config));
            app.setAppName(resolveRuntimeAppName(snapshot, command, businessObject));
            app.setAppType("BUSINESS");
            app.setEntryMode("RUNTIME");
            app.setEntryUrl(resolveEntryUrl(config.getConfigKey(), snapshot.mountTarget()));
            app.setIcon(StringUtils.defaultIfBlank(businessObject.getIcon(), "ionicons5:AppsOutline"));
            app.setDescription(StringUtils.defaultIfBlank(
                    snapshot.tableComment(), "低代码发布生成的标准业务应用入口"));
            app.setStatus(EnableStatus.ENABLED.getCode());
            app.setSortOrder(snapshot.menuSort());
            app.setOptions("{\"source\":\"lowcode_publish\"}");
        }
        boolean changed = !Objects.equals(app.getSuiteCode(), suiteCode)
                || !Objects.equals(app.getObjectCode(), businessObject.getObjectCode())
                || !Objects.equals(app.getConfigKey(), config.getConfigKey());
        app.setSuiteCode(suiteCode);
        app.setObjectCode(businessObject.getObjectCode());
        app.setConfigKey(config.getConfigKey());
        if (create) {
            requireSingleWrite(businessAppMapper.insert(app), "业务应用入口");
        } else if (changed) {
            requireSingleWrite(businessAppMapper.updateById(app), "业务应用入口");
        }
    }

    private Long requireResourceIdentity(Long resourceId, String resourceName) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException("低代码发布后置" + resourceName + "写入失败");
        }
        return resourceId;
    }

    private void requireSingleWrite(int affectedRows, String resourceName) {
        if (affectedRows != 1) {
            throw new BusinessException("低代码发布后置" + resourceName + "写入失败");
        }
    }

    private Long readMobileMenuResourceId(AiCrudConfig config) {
        if (StringUtils.isBlank(config.getOptions())) {
            return null;
        }
        try {
            return JSONObject.parseObject(config.getOptions()).getLong("mobileMenuResourceId");
        } catch (Exception invalidOptions) {
            return null;
        }
    }

    private void writeMobileMenuResourceId(AiCrudConfig config, Long mobileMenuResourceId) {
        JSONObject options;
        try {
            options = StringUtils.isNotBlank(config.getOptions())
                    ? JSONObject.parseObject(config.getOptions()) : new JSONObject();
        } catch (Exception invalidOptions) {
            options = new JSONObject();
        }
        if (mobileMenuResourceId == null) {
            options.remove("mobileMenuResourceId");
        } else {
            options.put("mobileMenuResourceId", mobileMenuResourceId);
        }
        config.setOptions(options.toJSONString());
    }

    private String resolveRuntimeAppName(PublishedSnapshot snapshot,
                                         LowcodePublishPostCommand command,
                                         AiBusinessObject businessObject) {
        return StringUtils.firstNonBlank(
                snapshot.menuName(), snapshot.appName(), command.businessObjectName(),
                snapshot.objectName(), businessObject.getObjectName(), command.configKey());
    }

    private String resolveRuntimeAppCode(Long tenantId,
                                         String suiteCode,
                                         String objectCode,
                                         AiCrudConfig config) {
        String base = normalizeAppCode(suiteCode + "_" + objectCode + "_RUNTIME");
        if (businessAppMapper.countByAppCode(tenantId, base, null) == 0) {
            return base;
        }
        String fallback = normalizeAppCode(base + "_" + config.getId());
        if (businessAppMapper.countByAppCode(tenantId, fallback, null) == 0) {
            return fallback;
        }
        return normalizeAppCode(base + "_" + System.currentTimeMillis());
    }

    private String normalizeAppCode(String value) {
        String normalized = StringUtils.defaultString(value)
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT)
                .replaceAll("^[^A-Z]+", "")
                .replaceAll("_+$", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = "LOWCODE_RUNTIME_APP";
        }
        return normalized.length() > 64
                ? normalized.substring(0, 64).replaceAll("_+$", "") : normalized;
    }

    private String resolveEntryUrl(String configKey, String mountTarget) {
        if (shouldMountMobile(mountTarget) && !shouldMountAdmin(mountTarget)) {
            return "/pages/lowcode-runtime?configKey=" + configKey;
        }
        return "/ai/crud-page/" + configKey;
    }

    private PublishedSnapshot loadPublishedSnapshot(LowcodePublishPostCommand command) {
        AiCrudConfigVersion version = versionMapper.selectVersionById(
                command.tenantId(), command.configId(), command.versionId());
        if (version == null
                || !Objects.equals(command.tenantId(), version.getTenantId())
                || !Objects.equals(command.configKey(), version.getConfigKey())
                || !Objects.equals(command.versionNo(), version.getVersionNo())) {
            throw new BusinessException("低代码发布版本快照身份无效");
        }
        JSONObject snapshot;
        try {
            snapshot = JSONObject.parseObject(version.getPublishSnapshot());
        } catch (Exception invalidSnapshot) {
            throw new BusinessException("低代码发布版本快照格式无效");
        }
        if (snapshot == null) {
            throw new BusinessException("低代码发布版本快照缺失");
        }
        String tableComment = StringUtils.trimToNull(snapshot.getString("tableComment"));
        String appName = StringUtils.firstNonBlank(
                snapshot.getString("appName"), tableComment, command.configKey());
        String menuName = StringUtils.firstNonBlank(
                snapshot.getString("menuName"), appName, tableComment, command.configKey());
        return new PublishedSnapshot(
                StringUtils.defaultIfBlank(snapshot.getString("mountTarget"), MOUNT_ADMIN),
                menuName,
                appName,
                tableComment,
                snapshot.getInteger("menuSort") == null ? 0 : snapshot.getInteger("menuSort"),
                version.getDomainId(),
                StringUtils.trimToNull(version.getDomainCode()),
                StringUtils.trimToNull(version.getObjectCode()),
                StringUtils.trimToNull(version.getObjectName()));
    }

    private boolean shouldMountAdmin(String mountTarget) {
        return MOUNT_ADMIN.equalsIgnoreCase(mountTarget) || MOUNT_BOTH.equalsIgnoreCase(mountTarget);
    }

    private boolean shouldMountMobile(String mountTarget) {
        return MOUNT_MOBILE.equalsIgnoreCase(mountTarget) || MOUNT_BOTH.equalsIgnoreCase(mountTarget);
    }

    private void validateCommand(LowcodePublishPostCommand command) {
        if (command == null || command.protocolVersion() == null
                || command.protocolVersion() != LowcodePublishTaskService.COMMAND_PROTOCOL_VERSION
                || command.tenantId() == null || command.tenantId() <= 0
                || command.configId() == null || StringUtils.isBlank(command.configKey())
                || command.versionId() == null || command.versionNo() == null
                || command.operatorId() == null || command.operatorId() <= 0) {
            throw new BusinessException("低代码发布后置命令身份无效");
        }
    }

    public enum Result {
        COMPLETED,
        SUPERSEDED
    }

    private record PublishedSnapshot(
            String mountTarget,
            String menuName,
            String appName,
            String tableComment,
            Integer menuSort,
            Long domainId,
            String domainCode,
            String objectCode,
            String objectName
    ) {
    }
}
