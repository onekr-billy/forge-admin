package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDomainRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeObjectSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePolicySchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.vo.lowcode.LowcodeVersionVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 低代码应用发布、版本和回滚服务。
 */
@Service
public class LowcodePublishService {

    private static final String DEPLOY_SKIP_DDL = "SKIP_DDL";
    private static final String DEPLOY_ONLINE_CREATE_TABLE = "ONLINE_CREATE_TABLE";
    private static final String DDL_PERMISSION = "ai:lowcode:deploy-ddl";
    private static final String GENERAL_DOMAIN_CODE = "general";
    private static final String OPERATION_PUBLISH = "PUBLISH";
    private static final String OPERATION_ROLLBACK = "ROLLBACK";
    private static final String MOUNT_ADMIN = "ADMIN";
    private static final String MOUNT_BOTH = "BOTH";

    private final ObjectMapper objectMapper;
    private final AiCrudConfigService configService;
    private final LowcodeAppService appService;
    private final LowcodeDomainService domainService;
    private final LowcodeRuntimeConfigBuilder runtimeConfigBuilder;
    private final LowcodeSchemaValidator schemaValidator;
    private final LowcodeDdlService ddlService;
    private final LowcodePolicyService policyService;
    private final MenuRegisterAdapter menuRegisterAdapter;
    private final AiCrudConfigVersionMapper versionMapper;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;
    private final LowcodePublishTaskService publishTaskService;
    /**
     * 领域菜单父级缓存（domainId → menuParentId），发布过程中多次递归查询时避免重复读写 sys_resource。
     */
    private final Map<Long, Long> domainMenuParentIdCache = new ConcurrentHashMap<>();

    public LowcodePublishService(ObjectMapper objectMapper,
                                 AiCrudConfigService configService,
                                 LowcodeAppService appService,
                                 LowcodeDomainService domainService,
                                 LowcodeRuntimeConfigBuilder runtimeConfigBuilder,
                                 LowcodeSchemaValidator schemaValidator,
                                 LowcodeDdlService ddlService,
                                 LowcodePolicyService policyService,
                                 MenuRegisterAdapter menuRegisterAdapter,
                                 AiCrudConfigVersionMapper versionMapper,
                                 LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver,
                                 LowcodePublishTaskService publishTaskService) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.appService = appService;
        this.domainService = domainService;
        this.runtimeConfigBuilder = runtimeConfigBuilder;
        this.schemaValidator = schemaValidator;
        this.ddlService = ddlService;
        this.policyService = policyService;
        this.menuRegisterAdapter = menuRegisterAdapter;
        this.versionMapper = versionMapper;
        this.runtimeDataSourceResolver = runtimeDataSourceResolver;
        this.publishTaskService = publishTaskService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long id, LowcodePublishDTO dto) {
        Long tenantId = requireTenantId();
        AiCrudConfig config = appService.requireConfig(id);
        requireTenantId(config);
        LowcodeModelSchema modelSchema = resolvePublishModel(config, dto);
        PublishDomainContext domainContext = resolvePublishDomainContext(config, modelSchema);
        applyDomainToModelSchema(modelSchema, domainContext, config.getConfigKey());
        LowcodePageSchema pageSchema = resolvePublishPage(config, dto, modelSchema);
        schemaValidator.validatePage(pageSchema, modelSchema);

        // 一次解析运行时数据源上下文，后续所有依赖都复用同一份 context，避免 3-4 次重复查询
        LowcodeRuntimeDataSourceContext runtimeContext = runtimeDataSourceResolver.resolve(modelSchema);
        ensureTableReady(modelSchema, dto, runtimeContext);
        // 去掉列校验（FOLLOW_SYSTEM 策略列已由设计器保证，不必再走 listColumns information_schema 查询）
        policyService.normalizeModelSchema(modelSchema);

        LowcodeRuntimeConfig runtimeConfig = runtimeConfigBuilder.buildRuntimeConfig(config.getConfigKey(), modelSchema, pageSchema);
        applyRuntimeConfig(config, modelSchema, pageSchema, runtimeConfig, runtimeContext);
        applyDomainToConfig(config, domainContext);
        applyMenuConfig(config, dto);
        config.setMountTarget(resolveMountTarget(dto, config));

        // 预先解析菜单父级 ID（主事务内），用于事务提交后异步执行菜单注册
        Long menuParentId = null;
        boolean syncMenu = shouldSyncMenu(dto);
        if (syncMenu && shouldMountAdmin(config.getMountTarget())) {
            applyPublishMenuParent(config, dto, domainContext.domain());
            menuParentId = config.getMenuParentId();
        }

        int versionNo = nextVersionNo(config, tenantId);
        config.setPublishStatus("PUBLISHED");
        config.setPublishedVersion(versionNo);
        config.setPublishTime(LocalDateTime.now());
        config.setPublishBy(SessionHelper.getUserId());
        configService.updateById(config);
        AiCrudConfigVersion version = createVersion(config, tenantId, versionNo, "publish",
                dto != null ? dto.getRemark() : null);
        publishTaskService.appendPostSync(
                config, dto, version, OPERATION_PUBLISH, syncMenu, menuParentId);
        return version.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, Long versionId) {
        rollback(id, versionId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, Long versionId, boolean syncMenu) {
        Long tenantId = requireTenantId();
        AiCrudConfig config = appService.requireConfig(id);
        requireTenantId(config);
        AiCrudConfigVersion targetVersion = versionMapper.selectVersionById(
                tenantId, config.getId(), versionId);
        if (targetVersion == null) {
            throw new BusinessException("版本不存在或不属于当前应用");
        }

        Map<String, Object> snapshot = readSnapshot(targetVersion.getPublishSnapshot());
        LowcodeModelSchema modelSchema = readVersionModel(targetVersion);
        PublishDomainContext domainContext = resolveVersionDomainContext(config, targetVersion, snapshot, modelSchema);
        applyDomainToModelSchema(modelSchema, domainContext, config.getConfigKey());
        LowcodePageSchema pageSchema = readVersionPage(targetVersion);

        // 一次解析运行时数据源上下文，后续所有依赖都复用同一份 context
        LowcodeRuntimeDataSourceContext runtimeContext = runtimeDataSourceResolver.resolve(modelSchema);
        LowcodeRuntimeConfig runtimeConfig = runtimeConfigBuilder.buildRuntimeConfig(config.getConfigKey(), modelSchema, pageSchema);
        applyRuntimeConfig(config, modelSchema, pageSchema, runtimeConfig, runtimeContext);
        applyDomainToConfig(config, domainContext);
        applyVersionRuntimeFields(config, targetVersion, snapshot, runtimeConfig);
        applySnapshotMenuFields(config, snapshot);

        Long menuParentId = null;
        if (syncMenu) {
            if (shouldMountAdmin(config.getMountTarget())) {
                Long parentId = config.getMenuParentId() != null
                        ? config.getMenuParentId()
                        : resolveDomainMenuParentId(resolveDomainForConfig(config));
                if (parentId == null) {
                    parentId = menuRegisterAdapter.resolveDefaultLowcodeParentId();
                }
                config.setMenuParentId(parentId);
                menuParentId = parentId;
            }
            config.setMountTarget(StringUtils.defaultIfBlank(
                    text(snapshot.get("mountTarget")), config.getMountTarget()));
        }

        int versionNo = nextVersionNo(config, tenantId);
        config.setPublishStatus("PUBLISHED");
        config.setPublishedVersion(versionNo);
        config.setPublishTime(LocalDateTime.now());
        config.setPublishBy(SessionHelper.getUserId());
        configService.updateById(config);
        AiCrudConfigVersion version = createVersion(
                config, tenantId, versionNo, "rollback", "回滚到版本 " + targetVersion.getVersionNo());
        publishTaskService.appendPostSync(
                config, null, version, OPERATION_ROLLBACK, syncMenu, menuParentId);
    }

    public List<LowcodeVersionVO> listVersions(Long id) {
        Long tenantId = requireTenantId();
        AiCrudConfig config = appService.requireConfig(id);
        requireTenantId(config);
        return versionMapper.selectByConfigId(tenantId, config.getId()).stream()
                .map(this::toVersionVO)
                .toList();
    }

    private LowcodeModelSchema resolvePublishModel(AiCrudConfig config, LowcodePublishDTO dto) {
        if (dto != null && dto.getModelSchema() != null) {
            return dto.getModelSchema();
        }
        return appService.readModelSchema(config);
    }

    private LowcodePageSchema resolvePublishPage(AiCrudConfig config, LowcodePublishDTO dto,
                                                 LowcodeModelSchema modelSchema) {
        if (dto != null && dto.getPageSchema() != null) {
            return dto.getPageSchema();
        }
        if (StringUtils.isNotBlank(config.getPageSchema())) {
            return appService.readPageSchema(config);
        }
        return appService.buildDefaultPageSchema(modelSchema);
    }

    private void ensureTableReady(LowcodeModelSchema modelSchema,
                                  LowcodePublishDTO dto,
                                  LowcodeRuntimeDataSourceContext runtimeContext) {
        String deployMode = dto != null && StringUtils.isNotBlank(dto.getDeployMode())
                ? dto.getDeployMode()
                : DEPLOY_SKIP_DDL;
        if (DEPLOY_ONLINE_CREATE_TABLE.equals(deployMode)) {
            if (!Boolean.TRUE.equals(dto.getConfirmOnlineDdl())) {
                throw new BusinessException("在线建表发布需要二次确认");
            }
            if (!SessionHelper.hasPermission(DDL_PERMISSION)) {
                throw new BusinessException("缺少在线建表发布权限: " + DDL_PERMISSION);
            }
            ddlService.executeCreateTable(modelSchema);
            return;
        }
        // 复用外部已解析的 runtimeContext，避免每个 ddl 校验方法再单独 resolve 一次
        if (!ddlService.tableExists(modelSchema, runtimeContext)) {
            throw new BusinessException("数据表不存在，请先在数据模型页同步表结构");
        }
        if (!ddlService.hasSinglePrimaryKey(modelSchema, runtimeContext)) {
            throw new BusinessException("数据表缺少单字段主键，请先在数据模型页修正表结构");
        }
    }

    private void applyRuntimeConfig(AiCrudConfig config,
                                    LowcodeModelSchema modelSchema,
                                    LowcodePageSchema pageSchema,
                                    LowcodeRuntimeConfig runtimeConfig,
                                    LowcodeRuntimeDataSourceContext runtimeContext) {
        config.setTableName(runtimeConfig.getTableName());
        config.setTableComment(runtimeConfig.getTableComment());
        config.setAppName(StringUtils.defaultIfBlank(config.getAppName(), runtimeConfig.getTableComment()));
        config.setMode("CONFIG");
        config.setBuildMode("LOWCODE");
        config.setStatus(EnableStatus.DISABLED.codeAsString());
        config.setLayoutType(runtimeConfig.getLayoutType());
        config.setModelSchema(appService.writeJson(modelSchema, "modelSchema"));
        config.setPageSchema(appService.writeJson(pageSchema, "pageSchema"));
        config.setSearchSchema(runtimeConfig.getSearchSchema());
        config.setColumnsSchema(runtimeConfig.getColumnsSchema());
        config.setEditSchema(runtimeConfig.getEditSchema());
        config.setApiConfig(runtimeConfig.getApiConfig());
        config.setOptions(runtimeConfig.getOptions());
        config.setDictConfig(runtimeConfig.getDictConfig());
        config.setDesensitizeConfig(runtimeConfig.getDesensitizeConfig());
        config.setEncryptConfig(runtimeConfig.getEncryptConfig());
        config.setTransConfig(runtimeConfig.getTransConfig());
        applyRuntimeDatasourceConfig(config, runtimeContext);
    }

    private void applyRuntimeDatasourceConfig(AiCrudConfig config, LowcodeRuntimeDataSourceContext context) {
        config.setRuntimeDatasourceId(context.getDatasourceId());
        config.setRuntimeDatasourceCode(context.getDatasourceCode());
        config.setRuntimeDatasourceSnapshot(context.getSnapshot() == null
                ? null
                : appService.writeJson(context.getSnapshot(), "runtimeDatasourceSnapshot"));
        config.setRuntimeTableName(StringUtils.defaultIfBlank(context.getTableName(), config.getTableName()));
        config.setPrimaryKeyField(context.getPrimaryKey().getField());
        config.setPrimaryKeyColumn(context.getPrimaryKey().getColumnName());
        config.setPrimaryKeyType(context.getPrimaryKey().getDataType());
        config.setTenantStrategy(appService.writeJson(context.getTenantStrategy(), "tenantStrategy"));
        config.setAuditStrategy(appService.writeJson(context.getAuditStrategy(), "auditStrategy"));
        config.setLogicDeleteStrategy(appService.writeJson(context.getLogicDeleteStrategy(), "logicDeleteStrategy"));
    }

    private void applyMenuConfig(AiCrudConfig config, LowcodePublishDTO dto) {
        if (dto == null) {
            return;
        }
        if (StringUtils.isNotBlank(dto.getMenuName())) {
            config.setMenuName(dto.getMenuName());
        }
        if (dto.getMenuSort() != null) {
            config.setMenuSort(dto.getMenuSort());
        }
        if (StringUtils.isNotBlank(dto.getMountTarget())) {
            config.setMountTarget(dto.getMountTarget().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * 解析菜单挂载位置：优先使用发布请求中的值，其次使用配置已有值，默认 ADMIN。
     */
    private String resolveMountTarget(LowcodePublishDTO dto, AiCrudConfig config) {
        String fromDto = dto != null ? StringUtils.trimToNull(dto.getMountTarget()) : null;
        if (fromDto != null) {
            return fromDto.toUpperCase(Locale.ROOT);
        }
        return StringUtils.defaultIfBlank(config.getMountTarget(), MOUNT_ADMIN);
    }

    private boolean shouldMountAdmin(String mountTarget) {
        return MOUNT_ADMIN.equalsIgnoreCase(mountTarget) || MOUNT_BOTH.equalsIgnoreCase(mountTarget);
    }

    private boolean shouldSyncMenu(LowcodePublishDTO dto) {
        return dto == null || !Boolean.FALSE.equals(dto.getSyncMenu());
    }

    private int nextVersionNo(AiCrudConfig config, Long tenantId) {
        Integer maxVersionNo = versionMapper.selectMaxVersionNo(tenantId, config.getId());
        return (maxVersionNo == null ? 0 : maxVersionNo) + 1;
    }

    private AiCrudConfigVersion createVersion(AiCrudConfig config, Long tenantId,
                                               Integer versionNo, String versionType, String remark) {
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setTenantId(tenantId);
        version.setConfigId(config.getId());
        version.setConfigKey(config.getConfigKey());
        version.setDomainId(config.getDomainId());
        version.setDomainCode(config.getDomainCode());
        version.setObjectCode(config.getObjectCode());
        version.setObjectName(config.getObjectName());
        version.setVersionNo(versionNo);
        version.setVersionType(versionType);
        version.setModelSchema(config.getModelSchema());
        version.setPageSchema(config.getPageSchema());
        version.setSearchSchema(config.getSearchSchema());
        version.setColumnsSchema(config.getColumnsSchema());
        version.setEditSchema(config.getEditSchema());
        version.setApiConfig(config.getApiConfig());
        version.setOptions(config.getOptions());
        version.setRuntimeDatasourceId(config.getRuntimeDatasourceId());
        version.setRuntimeDatasourceCode(config.getRuntimeDatasourceCode());
        version.setRuntimeDatasourceSnapshot(config.getRuntimeDatasourceSnapshot());
        version.setRuntimeTableName(config.getRuntimeTableName());
        version.setPrimaryKeyField(config.getPrimaryKeyField());
        version.setPrimaryKeyColumn(config.getPrimaryKeyColumn());
        version.setPrimaryKeyType(config.getPrimaryKeyType());
        version.setTenantStrategy(config.getTenantStrategy());
        version.setAuditStrategy(config.getAuditStrategy());
        version.setLogicDeleteStrategy(config.getLogicDeleteStrategy());
        version.setPublishSnapshot(writeSnapshot(config));
        version.setRemark(StringUtils.defaultIfBlank(remark, "发布低代码应用"));
        versionMapper.insert(version);
        return version;
    }

    private void applyVersionRuntimeFields(AiCrudConfig config,
                                           AiCrudConfigVersion version,
                                           Map<String, Object> snapshot,
                                           LowcodeRuntimeConfig fallback) {
        config.setSearchSchema(StringUtils.defaultIfBlank(version.getSearchSchema(), fallback.getSearchSchema()));
        config.setColumnsSchema(StringUtils.defaultIfBlank(version.getColumnsSchema(), fallback.getColumnsSchema()));
        config.setEditSchema(StringUtils.defaultIfBlank(version.getEditSchema(), fallback.getEditSchema()));
        config.setApiConfig(StringUtils.defaultIfBlank(version.getApiConfig(), fallback.getApiConfig()));
        config.setOptions(StringUtils.defaultIfBlank(version.getOptions(), fallback.getOptions()));
        config.setDictConfig(StringUtils.defaultIfBlank(text(snapshot.get("dictConfig")), fallback.getDictConfig()));
        config.setDesensitizeConfig(StringUtils.defaultIfBlank(text(snapshot.get("desensitizeConfig")), fallback.getDesensitizeConfig()));
        config.setEncryptConfig(StringUtils.defaultIfBlank(text(snapshot.get("encryptConfig")), fallback.getEncryptConfig()));
        config.setTransConfig(StringUtils.defaultIfBlank(text(snapshot.get("transConfig")), fallback.getTransConfig()));
        config.setLayoutType(StringUtils.defaultIfBlank(text(snapshot.get("layoutType")), fallback.getLayoutType()));
        config.setTableName(StringUtils.defaultIfBlank(text(snapshot.get("tableName")), fallback.getTableName()));
        config.setTableComment(StringUtils.defaultIfBlank(text(snapshot.get("tableComment")), fallback.getTableComment()));
        config.setAppName(StringUtils.defaultIfBlank(text(snapshot.get("appName")), config.getTableComment()));
        if (version.getRuntimeDatasourceId() != null) {
            config.setRuntimeDatasourceId(version.getRuntimeDatasourceId());
        }
        config.setRuntimeDatasourceCode(StringUtils.defaultIfBlank(
                version.getRuntimeDatasourceCode(), config.getRuntimeDatasourceCode()));
        config.setRuntimeDatasourceSnapshot(StringUtils.defaultIfBlank(
                version.getRuntimeDatasourceSnapshot(), config.getRuntimeDatasourceSnapshot()));
        config.setRuntimeTableName(StringUtils.defaultIfBlank(
                version.getRuntimeTableName(), config.getRuntimeTableName()));
        config.setPrimaryKeyField(StringUtils.defaultIfBlank(
                version.getPrimaryKeyField(), config.getPrimaryKeyField()));
        config.setPrimaryKeyColumn(StringUtils.defaultIfBlank(
                version.getPrimaryKeyColumn(), config.getPrimaryKeyColumn()));
        config.setPrimaryKeyType(StringUtils.defaultIfBlank(
                version.getPrimaryKeyType(), config.getPrimaryKeyType()));
        config.setTenantStrategy(StringUtils.defaultIfBlank(
                version.getTenantStrategy(), config.getTenantStrategy()));
        config.setAuditStrategy(StringUtils.defaultIfBlank(
                version.getAuditStrategy(), config.getAuditStrategy()));
        config.setLogicDeleteStrategy(StringUtils.defaultIfBlank(
                version.getLogicDeleteStrategy(), config.getLogicDeleteStrategy()));
    }

    private void applySnapshotMenuFields(AiCrudConfig config, Map<String, Object> snapshot) {
        config.setMenuName(StringUtils.defaultIfBlank(text(snapshot.get("menuName")),
                StringUtils.defaultIfBlank(config.getAppName(), config.getTableComment())));
        config.setMenuParentId(numberAsLong(snapshot.get("menuParentId"), config.getMenuParentId()));
        config.setMenuSort(numberAsInteger(snapshot.get("menuSort"), config.getMenuSort()));
        config.setMountTarget(StringUtils.defaultIfBlank(
                text(snapshot.get("mountTarget")), config.getMountTarget()));
    }

    private LowcodeModelSchema readVersionModel(AiCrudConfigVersion version) {
        return readJson(version.getModelSchema(), LowcodeModelSchema.class, "版本modelSchema");
    }

    private LowcodePageSchema readVersionPage(AiCrudConfigVersion version) {
        return readJson(version.getPageSchema(), LowcodePageSchema.class, "版本pageSchema");
    }

    private String writeSnapshot(AiCrudConfig config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configKey", config.getConfigKey());
        snapshot.put("tableName", config.getTableName());
        snapshot.put("tableComment", config.getTableComment());
        snapshot.put("appName", config.getAppName());
        snapshot.put("layoutType", config.getLayoutType());
        snapshot.put("domainId", config.getDomainId());
        snapshot.put("domainCode", config.getDomainCode());
        snapshot.put("objectCode", config.getObjectCode());
        snapshot.put("objectName", config.getObjectName());
        snapshot.put("domain", buildDomainSnapshot(config));
        snapshot.put("object", buildObjectSnapshot(config));
        snapshot.put("dictConfig", config.getDictConfig());
        snapshot.put("desensitizeConfig", config.getDesensitizeConfig());
        snapshot.put("encryptConfig", config.getEncryptConfig());
        snapshot.put("transConfig", config.getTransConfig());
        snapshot.put("runtimeDatasourceId", config.getRuntimeDatasourceId());
        snapshot.put("runtimeDatasourceCode", config.getRuntimeDatasourceCode());
        snapshot.put("runtimeDatasourceSnapshot", config.getRuntimeDatasourceSnapshot());
        snapshot.put("runtimeTableName", config.getRuntimeTableName());
        snapshot.put("primaryKeyField", config.getPrimaryKeyField());
        snapshot.put("primaryKeyColumn", config.getPrimaryKeyColumn());
        snapshot.put("primaryKeyType", config.getPrimaryKeyType());
        snapshot.put("tenantStrategy", config.getTenantStrategy());
        snapshot.put("auditStrategy", config.getAuditStrategy());
        snapshot.put("logicDeleteStrategy", config.getLogicDeleteStrategy());
        snapshot.put("menuName", config.getMenuName());
        snapshot.put("menuParentId", config.getMenuParentId());
        snapshot.put("menuSort", config.getMenuSort());
        snapshot.put("menuResourceId", config.getMenuResourceId());
        snapshot.put("mountTarget", config.getMountTarget());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BusinessException("发布快照生成失败");
        }
    }

    private Map<String, Object> readSnapshot(String snapshotJson) {
        if (StringUtils.isBlank(snapshotJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("版本快照格式不正确");
        }
    }

    private <T> T readJson(String json, Class<T> type, String fieldName) {
        if (StringUtils.isBlank(json)) {
            throw new BusinessException(fieldName + "不能为空");
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException(fieldName + "格式不正确");
        }
    }

    private LowcodeVersionVO toVersionVO(AiCrudConfigVersion version) {
        LowcodeVersionVO vo = new LowcodeVersionVO();
        vo.setId(version.getId());
        vo.setConfigId(version.getConfigId());
        vo.setConfigKey(version.getConfigKey());
        vo.setDomainId(version.getDomainId());
        vo.setDomainCode(version.getDomainCode());
        vo.setObjectCode(version.getObjectCode());
        vo.setObjectName(version.getObjectName());
        vo.setVersionNo(version.getVersionNo());
        vo.setVersionType(version.getVersionType());
        vo.setRemark(version.getRemark());
        vo.setCreateTime(version.getCreateTime());
        vo.setCreateBy(version.getCreateBy());
        return vo;
    }

    private Long requireTenantId() {
        return LowcodeTenantContext.requireTenantId("低代码应用发布");
    }

    private Long requireTenantId(AiCrudConfig config) {
        return LowcodeTenantContext.requireConfigTenant(config, "低代码应用发布");
    }

    private PublishDomainContext resolvePublishDomainContext(AiCrudConfig config, LowcodeModelSchema modelSchema) {
        LowcodeDomainRef schemaDomain = modelSchema != null ? modelSchema.getDomain() : null;
        LowcodeObjectSchema schemaObject = modelSchema != null ? modelSchema.getObject() : null;
        Long domainId = firstNonNull(config.getDomainId(), schemaDomain != null ? schemaDomain.getId() : null);
        String domainCode = StringUtils.firstNonBlank(config.getDomainCode(), schemaDomain != null ? schemaDomain.getCode() : null);
        AiLowcodeDomain domain = resolveDomain(domainId, domainCode);
        String objectCode = StringUtils.firstNonBlank(config.getObjectCode(),
                schemaObject != null ? schemaObject.getCode() : null,
                config.getConfigKey(),
                modelSchema != null ? modelSchema.getTableName() : null);
        String objectName = StringUtils.firstNonBlank(config.getObjectName(),
                schemaObject != null ? schemaObject.getName() : null,
                modelSchema != null ? modelSchema.getBusinessName() : null,
                config.getAppName(),
                config.getTableComment(),
                objectCode);
        return new PublishDomainContext(domain, objectCode, objectName);
    }

    private PublishDomainContext resolveVersionDomainContext(AiCrudConfig config,
                                                             AiCrudConfigVersion version,
                                                             Map<String, Object> snapshot,
                                                             LowcodeModelSchema modelSchema) {
        LowcodeDomainRef schemaDomain = modelSchema != null ? modelSchema.getDomain() : null;
        LowcodeObjectSchema schemaObject = modelSchema != null ? modelSchema.getObject() : null;
        Long domainId = firstNonNull(
                version.getDomainId(),
                numberAsLong(snapshot.get("domainId"), null),
                schemaDomain != null ? schemaDomain.getId() : null,
                config.getDomainId());
        String domainCode = StringUtils.firstNonBlank(
                version.getDomainCode(),
                text(snapshot.get("domainCode")),
                schemaDomain != null ? schemaDomain.getCode() : null,
                config.getDomainCode());
        AiLowcodeDomain domain = resolveDomain(domainId, domainCode);
        String objectCode = StringUtils.firstNonBlank(
                version.getObjectCode(),
                text(snapshot.get("objectCode")),
                schemaObject != null ? schemaObject.getCode() : null,
                config.getObjectCode(),
                config.getConfigKey());
        String objectName = StringUtils.firstNonBlank(
                version.getObjectName(),
                text(snapshot.get("objectName")),
                schemaObject != null ? schemaObject.getName() : null,
                config.getObjectName(),
                modelSchema != null ? modelSchema.getBusinessName() : null,
                config.getAppName(),
                config.getTableComment(),
                objectCode);
        return new PublishDomainContext(domain, objectCode, objectName);
    }

    private AiLowcodeDomain resolveDomainForConfig(AiCrudConfig config) {
        return resolveDomain(config.getDomainId(), config.getDomainCode());
    }

    private AiLowcodeDomain resolveDomain(Long domainId, String domainCode) {
        if (domainId != null) {
            try {
                return domainService.requireDomain(domainId);
            } catch (BusinessException ignored) {
                // 旧版本快照可能只保留编码，继续按编码和通用业务域兜底。
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
            throw new BusinessException("通用业务域不存在，请先执行低代码业务领域迁移脚本");
        }
        return generalDomain;
    }

    private void applyDomainToModelSchema(LowcodeModelSchema modelSchema, PublishDomainContext context, String configKey) {
        if (modelSchema == null || context == null || context.domain() == null) {
            return;
        }
        normalizeModelCollections(modelSchema);
        modelSchema.setSchemaVersion(2);

        LowcodeDomainRef domainRef = modelSchema.getDomain() == null ? new LowcodeDomainRef() : modelSchema.getDomain();
        domainRef.setId(context.domain().getId());
        domainRef.setCode(context.domain().getDomainCode());
        domainRef.setName(context.domain().getDomainName());
        modelSchema.setDomain(domainRef);

        LowcodeObjectSchema object = modelSchema.getObject() == null ? new LowcodeObjectSchema() : modelSchema.getObject();
        object.setCode(context.objectCode());
        object.setName(context.objectName());
        if (StringUtils.isBlank(object.getDescription())) {
            object.setDescription(StringUtils.defaultIfBlank(modelSchema.getBusinessName(), context.objectName()));
        }
        modelSchema.setObject(object);
        ensureTableName(modelSchema, context.domain(), context.objectCode(), configKey);
        policyService.normalizeModelSchema(modelSchema);
    }

    private void ensureTableName(LowcodeModelSchema modelSchema, AiLowcodeDomain domain, String objectCode, String configKey) {
        if (schemaValidator.isValidTableName(modelSchema.getTableName())) {
            return;
        }
        String prefix = StringUtils.defaultIfBlank(domain.getTablePrefix(), "biz_");
        String base = StringUtils.firstNonBlank(
                objectCode,
                modelSchema.getObject() == null ? null : modelSchema.getObject().getCode(),
                configKey,
                "runtime_model");
        modelSchema.setTableName(normalizeTableName(prefix + base));
    }

    private String normalizeTableName(String value) {
        String normalized = StringUtils.defaultString(value)
                .trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = "biz_lowcode_model";
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64).replaceAll("_+$", "");
        }
        return StringUtils.defaultIfBlank(normalized, "biz_lowcode_model");
    }

    private void normalizeModelCollections(LowcodeModelSchema modelSchema) {
        if (modelSchema.getFields() == null) {
            modelSchema.setFields(new ArrayList<>());
        }
        if (modelSchema.getRelations() == null) {
            modelSchema.setRelations(new ArrayList<>());
        }
        if (modelSchema.getPolicies() == null) {
            modelSchema.setPolicies(new LowcodePolicySchema());
        }
        if (modelSchema.getChildren() == null) {
            modelSchema.setChildren(new ArrayList<>());
        }
    }

    private void applyDomainToConfig(AiCrudConfig config, PublishDomainContext context) {
        if (context == null || context.domain() == null) {
            return;
        }
        config.setDomainId(context.domain().getId());
        config.setDomainCode(context.domain().getDomainCode());
        config.setObjectCode(context.objectCode());
        config.setObjectName(context.objectName());
    }

    private void applyPublishMenuParent(AiCrudConfig config, LowcodePublishDTO dto, AiLowcodeDomain domain) {
        Long domainParentId = resolveDomainMenuParentId(domain);
        Long requestParentId = dto == null ? null : dto.getMenuParentId();
        if (requestParentId != null && !requestParentId.equals(domainParentId)) {
            config.setMenuParentId(requestParentId);
            return;
        }
        config.setMenuParentId(domainParentId != null ? domainParentId : menuRegisterAdapter.resolveDefaultLowcodeParentId());
    }

    private Long resolveDomainMenuParentId(AiLowcodeDomain domain) {
        if (domain == null || domain.getId() == null) {
            return resolveDomainMenuParentIdUncached(domain, new HashSet<>());
        }
        return domainMenuParentIdCache.computeIfAbsent(domain.getId(),
                key -> resolveDomainMenuParentIdUncached(domain, new HashSet<>()));
    }

    private Long resolveDomainMenuParentIdUncached(AiLowcodeDomain domain, Set<Long> resolvingDomainIds) {
        if (domain == null) {
            return null;
        }
        Long domainId = domain.getId();
        if (domainId != null && !resolvingDomainIds.add(domainId)) {
            throw new BusinessException("业务领域层级存在循环引用，请先调整领域父级");
        }
        try {
            Long parentMenuId = menuRegisterAdapter.resolveDefaultLowcodeParentId();
            Long parentDomainId = domain.getParentId();
            if (parentDomainId != null && parentDomainId > 0) {
                AiLowcodeDomain parentDomain = domainService.requireDomain(parentDomainId);
                Long resolvedParentMenuId = resolveDomainMenuParentIdUncached(parentDomain, resolvingDomainIds);
                if (resolvedParentMenuId != null) {
                    parentMenuId = resolvedParentMenuId;
                }
            }
            Long menuParentId = menuRegisterAdapter.resolveOrCreateDomainParentId(
                    domain.getDomainCode(), domain.getDomainName(), domain.getSort(), parentMenuId);
            return menuParentId;
        } finally {
            if (domainId != null) {
                resolvingDomainIds.remove(domainId);
            }
        }
    }

    private Map<String, Object> buildDomainSnapshot(AiCrudConfig config) {
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("id", config.getDomainId());
        domain.put("code", config.getDomainCode());
        return domain;
    }

    private Map<String, Object> buildObjectSnapshot(AiCrudConfig config) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("code", config.getObjectCode());
        object.put("name", config.getObjectName());
        return object;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long numberAsLong(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Long.valueOf(text);
        }
        return defaultValue;
    }

    private Integer numberAsInteger(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Integer.valueOf(text);
        }
        return defaultValue;
    }

    record PublishDomainContext(AiLowcodeDomain domain, String objectCode, String objectName) {
    }
}
