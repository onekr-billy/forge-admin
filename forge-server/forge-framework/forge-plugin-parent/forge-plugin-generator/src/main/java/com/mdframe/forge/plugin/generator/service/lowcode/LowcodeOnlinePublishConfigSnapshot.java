package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 在线发布跨 DDL/配置事务恢复所需的不可变配置快照。 */
@Data
@NoArgsConstructor
public class LowcodeOnlinePublishConfigSnapshot {

    private String tableName;
    private String tableComment;
    private String appName;
    private String searchSchema;
    private String columnsSchema;
    private String editSchema;
    private String apiConfig;
    private String options;
    private String mode;
    private String buildMode;
    private String status;
    private String menuName;
    private Long menuParentId;
    private Integer menuSort;
    private String mountTarget;
    private String dictConfig;
    private String desensitizeConfig;
    private String encryptConfig;
    private String transConfig;
    private String layoutType;
    private String modelSchema;
    private String pageSchema;
    private Long domainId;
    private String domainCode;
    private String objectCode;
    private String objectName;
    private Long runtimeDatasourceId;
    private String runtimeDatasourceCode;
    private String runtimeDatasourceSnapshot;
    private String runtimeTableName;
    private String primaryKeyField;
    private String primaryKeyColumn;
    private String primaryKeyType;
    private String tenantStrategy;
    private String auditStrategy;
    private String logicDeleteStrategy;

    public static LowcodeOnlinePublishConfigSnapshot capture(AiCrudConfig config) {
        LowcodeOnlinePublishConfigSnapshot snapshot = new LowcodeOnlinePublishConfigSnapshot();
        snapshot.tableName = config.getTableName();
        snapshot.tableComment = config.getTableComment();
        snapshot.appName = config.getAppName();
        snapshot.searchSchema = config.getSearchSchema();
        snapshot.columnsSchema = config.getColumnsSchema();
        snapshot.editSchema = config.getEditSchema();
        snapshot.apiConfig = config.getApiConfig();
        snapshot.options = config.getOptions();
        snapshot.mode = config.getMode();
        snapshot.buildMode = config.getBuildMode();
        snapshot.status = config.getStatus();
        snapshot.menuName = config.getMenuName();
        snapshot.menuParentId = config.getMenuParentId();
        snapshot.menuSort = config.getMenuSort();
        snapshot.mountTarget = config.getMountTarget();
        snapshot.dictConfig = config.getDictConfig();
        snapshot.desensitizeConfig = config.getDesensitizeConfig();
        snapshot.encryptConfig = config.getEncryptConfig();
        snapshot.transConfig = config.getTransConfig();
        snapshot.layoutType = config.getLayoutType();
        snapshot.modelSchema = config.getModelSchema();
        snapshot.pageSchema = config.getPageSchema();
        snapshot.domainId = config.getDomainId();
        snapshot.domainCode = config.getDomainCode();
        snapshot.objectCode = config.getObjectCode();
        snapshot.objectName = config.getObjectName();
        snapshot.runtimeDatasourceId = config.getRuntimeDatasourceId();
        snapshot.runtimeDatasourceCode = config.getRuntimeDatasourceCode();
        snapshot.runtimeDatasourceSnapshot = config.getRuntimeDatasourceSnapshot();
        snapshot.runtimeTableName = config.getRuntimeTableName();
        snapshot.primaryKeyField = config.getPrimaryKeyField();
        snapshot.primaryKeyColumn = config.getPrimaryKeyColumn();
        snapshot.primaryKeyType = config.getPrimaryKeyType();
        snapshot.tenantStrategy = config.getTenantStrategy();
        snapshot.auditStrategy = config.getAuditStrategy();
        snapshot.logicDeleteStrategy = config.getLogicDeleteStrategy();
        return snapshot;
    }

    public void applyTo(AiCrudConfig config) {
        config.setTableName(tableName);
        config.setTableComment(tableComment);
        config.setAppName(appName);
        config.setSearchSchema(searchSchema);
        config.setColumnsSchema(columnsSchema);
        config.setEditSchema(editSchema);
        config.setApiConfig(apiConfig);
        config.setOptions(options);
        config.setMode(mode);
        config.setBuildMode(buildMode);
        config.setStatus(status);
        config.setMenuName(menuName);
        config.setMenuParentId(menuParentId);
        config.setMenuSort(menuSort);
        config.setMountTarget(mountTarget);
        config.setDictConfig(dictConfig);
        config.setDesensitizeConfig(desensitizeConfig);
        config.setEncryptConfig(encryptConfig);
        config.setTransConfig(transConfig);
        config.setLayoutType(layoutType);
        config.setModelSchema(modelSchema);
        config.setPageSchema(pageSchema);
        config.setDomainId(domainId);
        config.setDomainCode(domainCode);
        config.setObjectCode(objectCode);
        config.setObjectName(objectName);
        config.setRuntimeDatasourceId(runtimeDatasourceId);
        config.setRuntimeDatasourceCode(runtimeDatasourceCode);
        config.setRuntimeDatasourceSnapshot(runtimeDatasourceSnapshot);
        config.setRuntimeTableName(runtimeTableName);
        config.setPrimaryKeyField(primaryKeyField);
        config.setPrimaryKeyColumn(primaryKeyColumn);
        config.setPrimaryKeyType(primaryKeyType);
        config.setTenantStrategy(tenantStrategy);
        config.setAuditStrategy(auditStrategy);
        config.setLogicDeleteStrategy(logicDeleteStrategy);
    }

    public String toPublishSnapshotJson(ObjectMapper objectMapper, String configKey) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("configKey", configKey);
        snapshot.put("tableName", tableName);
        snapshot.put("tableComment", tableComment);
        snapshot.put("appName", appName);
        snapshot.put("layoutType", layoutType);
        snapshot.put("domainId", domainId);
        snapshot.put("domainCode", domainCode);
        snapshot.put("objectCode", objectCode);
        snapshot.put("objectName", objectName);
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("id", domainId);
        domain.put("code", domainCode);
        snapshot.put("domain", domain);
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("code", objectCode);
        object.put("name", objectName);
        snapshot.put("object", object);
        snapshot.put("dictConfig", dictConfig);
        snapshot.put("desensitizeConfig", desensitizeConfig);
        snapshot.put("encryptConfig", encryptConfig);
        snapshot.put("transConfig", transConfig);
        snapshot.put("runtimeDatasourceId", runtimeDatasourceId);
        snapshot.put("runtimeDatasourceCode", runtimeDatasourceCode);
        snapshot.put("runtimeDatasourceSnapshot", runtimeDatasourceSnapshot);
        snapshot.put("runtimeTableName", runtimeTableName);
        snapshot.put("primaryKeyField", primaryKeyField);
        snapshot.put("primaryKeyColumn", primaryKeyColumn);
        snapshot.put("primaryKeyType", primaryKeyType);
        snapshot.put("tenantStrategy", tenantStrategy);
        snapshot.put("auditStrategy", auditStrategy);
        snapshot.put("logicDeleteStrategy", logicDeleteStrategy);
        snapshot.put("menuName", menuName);
        snapshot.put("menuParentId", menuParentId);
        snapshot.put("menuSort", menuSort);
        snapshot.put("mountTarget", mountTarget);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception serializationFailure) {
            throw new BusinessException("在线发布配置快照序列化失败");
        }
    }
}
