package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePolicySchema;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePolicyService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.datascope.enums.DataScopeType;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态低代码运行时的数据权限适配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDataScopeService {

    private static final String DS_USER_ID = "__dsUserId";
    private static final String DS_TENANT_ID = "__dsTenantId";
    private static final String DS_ORG_IDS = "__dsOrgIds";
    private static final String DS_REGION_CODE = "__dsRegionCode";

    private final IDataScopeService dataScopeService;
    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;
    private final LowcodePolicyService policyService;

    public DynamicCrudRepository.SqlCondition buildCondition(AiCrudConfig config, String tableName, String tableAlias) {
        return buildCondition(config, tableName, tableAlias, null);
    }

    public DynamicCrudRepository.SqlCondition buildCondition(AiCrudConfig config,
                                                             String tableName,
                                                             String tableAlias,
                                                             DataScopeContext explicitContext) {
        LowcodeModelSchema modelSchema = readModelSchema(config);
        if (modelSchema == null) {
            logSkip(config, tableName, "非低代码配置或缺少模型协议");
            return null;
        }
        LowcodePolicySchema policies = policyService.normalizeModelSchema(modelSchema);
        if (!policyService.isFollowSystem(policies)) {
            logSkip(config, tableName, "未启用 FOLLOW_SYSTEM 策略: dataScope=" + policies.getDataScope());
            return null;
        }

        DataScopeContext context = explicitContext != null ? explicitContext : resolveContext(config);
        DataScopeType scopeType = DataScopeType.getByRoleDataScope(
                context.getMinDataScope(),
                context.getCustomOrgIds() != null && !context.getCustomOrgIds().isEmpty());
        if (scopeType == null) {
            throw new BusinessException("未知的数据权限范围: " + context.getMinDataScope());
        }
        if (scopeType == DataScopeType.ALL) {
            logSkip(config, tableName, "当前用户为全部数据权限");
            return null;
        }
        if (scopeType == DataScopeType.REGION && Integer.valueOf(1).equals(context.getRegionLevel())) {
            logSkip(config, tableName, "省级行政区划权限视为全部数据");
            return null;
        }

        DynamicCrudRepository.SqlCondition condition = switch (scopeType) {
            case SELF -> buildSelfCondition(tableName, tableAlias, policies, context);
            case ORG -> buildOrgCondition(tableName, tableAlias, policies, context.getOrgIds());
            case ORG_AND_CHILD -> buildOrgAndChildCondition(tableName, tableAlias, policies, context);
            case CUSTOM -> buildCustomOrgCondition(tableName, tableAlias, policies, context);
            case TENANT_ALL -> buildTenantCondition(tableName, tableAlias, policies, context);
            case REGION -> buildRegionCondition(tableName, tableAlias, policies, context);
            default -> null;
        };
        logApplied(config, tableName, tableAlias, scopeType, policies, context, condition);
        return condition;
    }

    private DynamicCrudRepository.SqlCondition buildSelfCondition(String tableName,
                                                                  String tableAlias,
                                                                  LowcodePolicySchema policies,
                                                                  DataScopeContext context) {
        String column = requireColumn(tableName, policies.getUserColumn(), "本人数据权限字段");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(DS_USER_ID, context.getUserId());
        return new DynamicCrudRepository.SqlCondition(qualify(tableAlias, column) + " = :" + DS_USER_ID, params);
    }

    private DynamicCrudRepository.SqlCondition buildOrgCondition(String tableName,
                                                                 String tableAlias,
                                                                 LowcodePolicySchema policies,
                                                                 List<Long> orgIds) {
        return buildOrgInCondition(tableName, tableAlias, policies, orgIds);
    }

    private DynamicCrudRepository.SqlCondition buildOrgAndChildCondition(String tableName,
                                                                         String tableAlias,
                                                                         LowcodePolicySchema policies,
                                                                         DataScopeContext context) {
        Set<Long> orgIds = dataScopeService.getOrgAndChildIds(context.getOrgIds());
        return buildOrgInCondition(tableName, tableAlias, policies,
                orgIds == null ? List.of() : new ArrayList<>(orgIds));
    }

    private DynamicCrudRepository.SqlCondition buildCustomOrgCondition(String tableName,
                                                                       String tableAlias,
                                                                       LowcodePolicySchema policies,
                                                                       DataScopeContext context) {
        Set<Long> customOrgIds = context.getCustomOrgIds();
        return buildOrgInCondition(tableName, tableAlias, policies,
                customOrgIds == null ? List.of() : new ArrayList<>(customOrgIds));
    }

    private DynamicCrudRepository.SqlCondition buildOrgInCondition(String tableName,
                                                                   String tableAlias,
                                                                   LowcodePolicySchema policies,
                                                                   List<Long> orgIds) {
        String column = requireColumn(tableName, policies.getOrgColumn(), "组织数据权限字段");
        if (orgIds == null || orgIds.isEmpty()) {
            return denyAll();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(DS_ORG_IDS, orgIds);
        return new DynamicCrudRepository.SqlCondition(qualify(tableAlias, column) + " IN (:" + DS_ORG_IDS + ")", params);
    }

    private DynamicCrudRepository.SqlCondition buildTenantCondition(String tableName,
                                                                    String tableAlias,
                                                                    LowcodePolicySchema policies,
                                                                    DataScopeContext context) {
        String column = requireColumn(tableName, policies.getTenantColumn(), "租户数据权限字段");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(DS_TENANT_ID, context.getTenantId());
        return new DynamicCrudRepository.SqlCondition(qualify(tableAlias, column) + " = :" + DS_TENANT_ID, params);
    }

    private DynamicCrudRepository.SqlCondition buildRegionCondition(String tableName,
                                                                    String tableAlias,
                                                                    LowcodePolicySchema policies,
                                                                    DataScopeContext context) {
        if (StringUtils.isBlank(context.getRegionCode())) {
            throw new BusinessException("当前用户缺少行政区划编码，无法执行行政区划数据权限");
        }
        String column = requireColumn(tableName, policies.getRegionColumn(), "行政区划数据权限字段");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(DS_REGION_CODE, context.getRegionCode());
        String qualifiedColumn = qualify(tableAlias, column);
        String sql = qualifiedColumn + " = :" + DS_REGION_CODE
                + " OR " + qualifiedColumn
                + " IN (SELECT code FROM sys_region_code WHERE parent_code = :" + DS_REGION_CODE + ")";
        return new DynamicCrudRepository.SqlCondition(sql, params);
    }

    private DynamicCrudRepository.SqlCondition denyAll() {
        return new DynamicCrudRepository.SqlCondition("1 = 0", Map.of());
    }

    private String requireColumn(String tableName, String columnName, String label) {
        if (StringUtils.isBlank(columnName)) {
            throw new BusinessException("FOLLOW_SYSTEM 策略缺少" + label + "映射");
        }
        repository.validateIdentifier(columnName);
        if (!repository.getTableColumns(tableName).contains(columnName)) {
            throw new BusinessException("FOLLOW_SYSTEM 策略" + label + "不存在: " + columnName);
        }
        return columnName;
    }

    private String qualify(String tableAlias, String columnName) {
        return StringUtils.isBlank(tableAlias) ? columnName : tableAlias + "." + columnName;
    }

    private DataScopeContext resolveContext(AiCrudConfig config) {
        DataScopeContext context;
        try {
            String objectCode = config == null ? null : StringUtils.trimToNull(config.getObjectCode());
            context = objectCode == null
                    ? dataScopeService.getCurrentUserDataScope()
                    : dataScopeService.getCurrentUserDataScope("ai:business:" + objectCode);
        } catch (Exception e) {
            log.warn("[DynamicDataScopeService] 获取当前用户数据权限失败, configKey={}",
                    config == null ? null : config.getConfigKey(), e);
            throw new BusinessException("未获取到当前用户数据权限上下文");
        }
        if (context == null || context.getUserId() == null) {
            throw new BusinessException("未获取到当前用户数据权限上下文");
        }
        return context;
    }

    private LowcodeModelSchema readModelSchema(AiCrudConfig config) {
        if (config == null || !"LOWCODE".equals(config.getBuildMode()) || StringUtils.isBlank(config.getModelSchema())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception e) {
            throw new BusinessException("低代码模型协议格式不正确");
        }
    }

    private void logSkip(AiCrudConfig config, String tableName, String reason) {
        log.debug("[DynamicDataScope] skip configKey={}, table={}, buildMode={}, publishStatus={}, reason={}",
                config == null ? null : config.getConfigKey(),
                tableName,
                config == null ? null : config.getBuildMode(),
                config == null ? null : config.getPublishStatus(),
                reason);
    }

    private void logApplied(AiCrudConfig config,
                            String tableName,
                            String tableAlias,
                            DataScopeType scopeType,
                            LowcodePolicySchema policies,
                            DataScopeContext context,
                            DynamicCrudRepository.SqlCondition condition) {
        if (condition == null) {
            logSkip(config, tableName, "数据范围未生成 SQL 条件: scopeType=" + scopeType);
            return;
        }
        log.debug("[DynamicDataScope] apply configKey={}, table={}, alias={}, scopeType={}, roleDataScope={}, "
                        + "tenantColumn={}, userColumn={}, orgColumn={}, regionColumn={}, sql={}, paramKeys={}",
                config == null ? null : config.getConfigKey(),
                tableName,
                tableAlias,
                scopeType,
                context.getMinDataScope(),
                policies.getTenantColumn(),
                policies.getUserColumn(),
                policies.getOrgColumn(),
                policies.getRegionColumn(),
                condition.sql(),
                condition.params() == null ? Set.of() : condition.params().keySet());
    }
}
