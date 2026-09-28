package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessSuite;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeModel;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeDomainMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessSuiteMapper;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;

/**
 * 低代码历史数据到业务应用平台的幂等映射服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessBootstrapService {

    private static final String DEFAULT_SUITE_CODE = "general";

    private final AiLowcodeDomainMapper lowcodeDomainMapper;
    private final AiLowcodeModelMapper lowcodeModelMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessSuiteMapper businessSuiteMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessAppMapper businessAppMapper;
    private final BusinessNamingService businessNamingService;

    @Transactional(rollbackFor = Exception.class)
    public void syncSuitesFromLowcodeDomains() {
        Long tenantId = resolveTenantId();
        List<AiLowcodeDomain> domains = lowcodeDomainMapper.selectDomainList(tenantId, null, null);
        for (AiLowcodeDomain domain : domains) {
            if (domain == null || StringUtils.isBlank(domain.getDomainCode())) {
                continue;
            }
            if (businessSuiteMapper.selectBySuiteCode(tenantId, domain.getDomainCode()) != null) {
                continue;
            }
            AiBusinessSuite suite = new AiBusinessSuite();
            suite.setTenantId(tenantId);
            suite.setSuiteCode(domain.getDomainCode());
            suite.setSuiteName(StringUtils.defaultIfBlank(domain.getDomainName(), domain.getDomainCode()));
            suite.setIcon(domain.getIcon());
            suite.setDescription(domain.getDomainDesc());
            suite.setStatus("DISABLED".equals(domain.getStatus())
                    ? EnableStatus.DISABLED.getCode() : EnableStatus.ENABLED.getCode());
            suite.setSortOrder(domain.getSort() == null ? 0 : domain.getSort());
            suite.setOptions("{\"source\":\"ai_lowcode_domain\"}");
            businessSuiteMapper.insert(suite);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncObjectsFromLowcodeModels() {
        syncSuitesFromLowcodeDomains();
        Long tenantId = resolveTenantId();
        List<AiLowcodeModel> models = lowcodeModelMapper.selectModelList(tenantId, null, null, null);
        for (AiLowcodeModel model : models) {
            if (model == null || StringUtils.isBlank(model.getModelCode())) {
                continue;
            }
            String suiteCode = StringUtils.defaultIfBlank(model.getDomainCode(), DEFAULT_SUITE_CODE);
            ensureSuite(tenantId, suiteCode);
            String requestedObjectCode = businessNamingService.normalizeObjectCode(
                    model.getModelCode(), StringUtils.defaultIfBlank(model.getModelName(), model.getModelCode()));
            AiBusinessObject existing = model.getId() == null
                    ? null : businessObjectMapper.selectByModelId(tenantId, model.getId());
            if (existing != null
                    || businessObjectMapper.selectByObjectCode(tenantId, suiteCode, requestedObjectCode) != null) {
                continue;
            }
            String objectCode = resolveUniqueObjectCode(tenantId, suiteCode, requestedObjectCode, model.getId());
            AiBusinessObject object = new AiBusinessObject();
            object.setTenantId(tenantId);
            object.setSuiteCode(suiteCode);
            object.setObjectCode(objectCode);
            object.setObjectName(StringUtils.defaultIfBlank(model.getModelName(), objectCode));
            object.setObjectType(Boolean.TRUE.equals(model.getMasterData()) ? "MASTER" : "TRANSACTION");
            object.setModelId(model.getId());
            object.setModelCode(model.getModelCode());
            object.setDescription(model.getModelDesc());
            object.setStatus("DISABLED".equals(model.getStatus())
                    ? EnableStatus.DISABLED.getCode() : EnableStatus.ENABLED.getCode());
            object.setSortOrder(0);
            object.setOptions("{\"source\":\"ai_lowcode_model\"}");
            insertObjectSafely(object);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncAppsFromPublishedCrudConfigs() {
        syncObjectsFromLowcodeModels();
        Long tenantId = resolveTenantId();
        List<AiCrudConfig> configs = crudConfigMapper.selectPublishedLowcodeConfigs(tenantId);
        for (AiCrudConfig config : configs) {
            if (config == null || StringUtils.isBlank(config.getConfigKey())) {
                continue;
            }
            if (businessAppMapper.selectByConfigKey(tenantId, config.getConfigKey()) != null) {
                continue;
            }
            String suiteCode = StringUtils.defaultIfBlank(config.getDomainCode(), DEFAULT_SUITE_CODE);
            String requestedObjectCode = StringUtils.defaultIfBlank(config.getObjectCode(), config.getConfigKey());
            ensureSuite(tenantId, suiteCode);
            AiBusinessObject object = ensureObject(tenantId, suiteCode, requestedObjectCode, config);

            AiBusinessApp app = new AiBusinessApp();
            app.setTenantId(tenantId);
            app.setAppCode(toAppCode(config.getConfigKey()));
            app.setAppName(resolveAppName(config));
            app.setAppType("BUSINESS");
            app.setSuiteCode(object.getSuiteCode());
            app.setObjectCode(object.getObjectCode());
            app.setEntryMode("RUNTIME");
            app.setEntryUrl("/ai/crud-page/" + config.getConfigKey());
            app.setConfigKey(config.getConfigKey());
            app.setIcon("ionicons5:AppsOutline");
            app.setDescription(StringUtils.defaultIfBlank(config.getTableComment(), "低代码已发布应用入口"));
            app.setStatus("STOPPED".equals(config.getPublishStatus())
                    ? EnableStatus.DISABLED.getCode() : EnableStatus.ENABLED.getCode());
            app.setSortOrder(config.getMenuSort() == null ? 0 : config.getMenuSort());
            app.setOptions("{\"source\":\"ai_crud_config\"}");
            businessAppMapper.insert(app);
        }
    }

    private void ensureSuite(Long tenantId, String suiteCode) {
        if (businessSuiteMapper.selectBySuiteCode(tenantId, suiteCode) != null) {
            return;
        }
        AiBusinessSuite suite = new AiBusinessSuite();
        suite.setTenantId(tenantId);
        suite.setSuiteCode(suiteCode);
        suite.setSuiteName(suiteCode);
        suite.setDescription("低代码历史数据自动生成的业务套件");
        suite.setStatus(EnableStatus.ENABLED.getCode());
        suite.setSortOrder(99);
        suite.setOptions("{\"source\":\"lowcode_compat\"}");
        businessSuiteMapper.insert(suite);
    }

    private AiBusinessObject ensureObject(Long tenantId, String suiteCode, String requestedObjectCode,
                                          AiCrudConfig config) {
        String objectCode = businessNamingService.normalizeObjectCode(
                requestedObjectCode, StringUtils.defaultIfBlank(config.getObjectName(), config.getConfigKey()));
        AiBusinessObject existingByConfigKey = businessObjectMapper.selectByConfigKey(tenantId, config.getConfigKey());
        if (existingByConfigKey != null) {
            return existingByConfigKey;
        }
        AiBusinessObject existing = businessObjectMapper.selectByObjectCode(tenantId, suiteCode, objectCode);
        if (existing != null) {
            bindConfigKeyIfBlank(tenantId, existing, config.getConfigKey());
            return existing;
        }
        if (StringUtils.isNotBlank(config.getObjectCode())) {
            existing = businessObjectMapper.selectBySuiteAndModelCode(
                    tenantId, suiteCode, StringUtils.trimToNull(config.getObjectCode()));
            if (existing != null) {
                bindConfigKeyIfBlank(tenantId, existing, config.getConfigKey());
                return existing;
            }
        }
        String globallyUniqueCode = resolveUniqueObjectCode(tenantId, suiteCode, objectCode, config.getId());
        AiBusinessObject object = new AiBusinessObject();
        object.setTenantId(tenantId);
        object.setSuiteCode(suiteCode);
        object.setObjectCode(globallyUniqueCode);
        object.setObjectName(StringUtils.defaultIfBlank(config.getObjectName(), resolveAppName(config)));
        object.setObjectType("TRANSACTION");
        object.setModelCode(config.getObjectCode());
        object.setConfigKey(config.getConfigKey());
        object.setDescription(config.getTableComment());
        object.setStatus(EnableStatus.ENABLED.getCode());
        object.setSortOrder(0);
        object.setOptions("{\"source\":\"ai_crud_config\"}");
        insertObjectSafely(object);
        return object;
    }

    /**
     * 低代码同步不走业务对象 Controller，必须在这里复用同一套租户级唯一约束。
     * 冲突时使用模型/配置主键生成稳定候选值，避免每次同步随机改名。
     */
    private String resolveUniqueObjectCode(Long tenantId, String suiteCode, String requestedObjectCode, Long sourceId) {
        String normalized = businessNamingService.normalizeObjectCode(requestedObjectCode, "business_object");
        if (businessObjectMapper.countActiveByObjectCode(tenantId, normalized, null) == 0) {
            return normalized;
        }
        String suffix = sourceId == null
                ? Integer.toUnsignedString((suiteCode + ":" + normalized).hashCode(), 36)
                : Long.toUnsignedString(sourceId, 36);
        String candidate = "bo_" + suffix;
        int attempt = 0;
        while (businessObjectMapper.countActiveByObjectCode(tenantId, candidate, null) > 0) {
            attempt++;
            candidate = "bo_" + suffix + "_" + attempt;
        }
        return candidate;
    }

    private void insertObjectSafely(AiBusinessObject object) {
        try {
            businessObjectMapper.insert(object);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("业务对象编码已存在（编码必须在租户内唯一）: " + object.getObjectCode(), exception);
        }
    }

    private void bindConfigKeyIfBlank(Long tenantId, AiBusinessObject object, String configKey) {
        if (object == null || StringUtils.isBlank(configKey) || StringUtils.isNotBlank(object.getConfigKey())) {
            return;
        }
        businessObjectMapper.bindConfigKey(tenantId, object.getId(), configKey);
        object.setConfigKey(configKey);
    }

    private String resolveAppName(AiCrudConfig config) {
        return StringUtils.defaultIfBlank(
                config.getAppName(),
                StringUtils.defaultIfBlank(config.getObjectName(),
                        StringUtils.defaultIfBlank(config.getTableComment(), config.getConfigKey()))
        );
    }

    private String toAppCode(String configKey) {
        String normalized = configKey.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("LC_")) {
            normalized = "LC_" + normalized;
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId != null ? tenantId : 1L;
    }
}
