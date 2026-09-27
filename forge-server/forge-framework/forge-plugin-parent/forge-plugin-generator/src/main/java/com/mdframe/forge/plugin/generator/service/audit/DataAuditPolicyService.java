package com.mdframe.forge.plugin.generator.service.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditPolicyDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditPolicyMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditCoverageItemVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditPolicyVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataAuditPolicyService {

    private final DataAuditPolicyMapper policyMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final ObjectMapper objectMapper;

    public DataAuditPolicyVO getPolicy(Long objectId) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        ensureIndex(tenantId);
        AiBusinessObject object = requireObject(tenantId, objectId);
        AiDataAuditPolicy policy = policyMapper.selectByObjectId(tenantId, objectId);
        DataAuditPolicyVO vo = new DataAuditPolicyVO();
        vo.setObjectId(String.valueOf(object.getId()));
        vo.setObjectCode(object.getObjectCode());
        vo.setObjectName(object.getObjectName());
        vo.setEnabled(policy != null && EnableStatus.ENABLED.matches(policy.getEnabled()));
        vo.setReasonRequired(policy == null || EnableStatus.ENABLED.matches(policy.getReasonRequired()));
        vo.setShowInDetail(policy == null || EnableStatus.ENABLED.matches(policy.getShowInDetail()));
        vo.setPolicyVersion(policy == null ? 0 : policy.getPolicyVersion());
        vo.setEnabledAt(policy == null ? null : policy.getEnabledAt());
        vo.setUpdatedAt(policy == null ? null : policy.getUpdateTime());
        vo.setCoverageStatus(policy == null ? "PENDING" : policy.getCoverageStatus());
        vo.setCoverageItems(evaluateCoverage(object).items());
        if (Boolean.TRUE.equals(vo.getEnabled())) {
            vo.setMessage("正在记录该对象后续提交的数据变化");
        } else if (vo.getEnabledAt() != null) {
            vo.setMessage("采集已停用，停用前的历史记录仍然保留");
        } else {
            vo.setMessage("尚未开始记录该对象的数据变化");
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataAuditPolicyVO updatePolicy(Long objectId, DataAuditPolicyDTO dto) {
        Long tenantId = DataAuditTenantSupport.currentTenantId();
        AiBusinessObject object = requireObject(tenantId, objectId);
        AiDataAuditPolicy policy = policyMapper.selectByObjectIdForUpdate(tenantId, objectId);
        boolean currentlyEnabled = policy != null && EnableStatus.ENABLED.matches(policy.getEnabled());
        boolean enable = dto != null && dto.getEnabled() != null
                ? Boolean.TRUE.equals(dto.getEnabled())
                : currentlyEnabled;
        boolean reasonRequired = dto == null || dto.getReasonRequired() == null
                ? policy == null || EnableStatus.ENABLED.matches(policy.getReasonRequired())
                : Boolean.TRUE.equals(dto.getReasonRequired());
        boolean showInDetail = dto == null || dto.getShowInDetail() == null
                ? policy == null || EnableStatus.ENABLED.matches(policy.getShowInDetail())
                : Boolean.TRUE.equals(dto.getShowInDetail());
        CoverageResult coverage = evaluateCoverage(object);
        if (enable && !coverage.passed()) {
            throw DataAuditErrorCode.AUDIT_UNSUPPORTED.exception(firstFailure(coverage));
        }
        if (policy == null) {
            policy = newPolicy(tenantId, objectId);
            policyMapper.insert(policy);
            policy = policyMapper.selectByObjectIdForUpdate(tenantId, objectId);
        }
        boolean statusChanged = currentlyEnabled != enable;
        if (statusChanged) {
            policy.setWriteBarrier(EnableStatus.ENABLED.getCode());
            policyMapper.updateById(policy);
            refreshIndex(tenantId);
        }
        policy.setEnabled(enable ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        policy.setReasonRequired(reasonRequired ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        policy.setShowInDetail(showInDetail ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        policy.setPolicyVersion(policy.getPolicyVersion() == null ? 1 : policy.getPolicyVersion() + 1);
        if (enable && policy.getEnabledAt() == null) {
            policy.setEnabledAt(LocalDateTime.now());
        }
        policy.setWriteBarrier(EnableStatus.DISABLED.getCode());
        policy.setCoverageStatus(coverage.passed() ? "PASSED" : "BLOCKED");
        policy.setCoverageJson(writeJson(coverage));
        AiCrudConfig config = resolveConfig(tenantId, object);
        policy.setPolicySnapshot(writeJson(Map.of(
                "tableName", config == null ? "" : StringUtils.defaultString(config.getTableName()),
                "configKey", StringUtils.defaultString(object.getConfigKey()),
                "enabled", enable,
                "reasonRequired", reasonRequired,
                "showInDetail", showInDetail,
                "enabledAt", policy.getEnabledAt() == null ? "" : policy.getEnabledAt().toString()
        )));
        policy.setUpdateBy(SessionHelper.getUserId());
        policy.setUpdateTime(LocalDateTime.now());
        policyMapper.updateById(policy);
        refreshIndex(tenantId);
        return getPolicy(objectId);
    }

    public void ensureIndex(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        // 索引已加载则复用；策略变更走 refreshIndex（updatePolicy 已调用）
        if (DataAuditTransactionHolder.index().hasTenant(tenantId)) {
            return;
        }
        refreshIndex(tenantId);
    }

    public void refreshIndex(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        List<AiDataAuditPolicy> policies = policyMapper.selectConfigured(tenantId);
        Map<String, DataAuditPolicyIndex.TableBinding> tables = new LinkedHashMap<>();
        Map<Long, DataAuditPolicyIndex.ObjectPolicy> objects = new LinkedHashMap<>();
        for (AiDataAuditPolicy policy : policies) {
            objects.put(policy.getObjectId(), new DataAuditPolicyIndex.ObjectPolicy(
                    policy.getObjectId(),
                    EnableStatus.ENABLED.matches(policy.getEnabled()),
                    EnableStatus.ENABLED.matches(policy.getReasonRequired()),
                    EnableStatus.ENABLED.matches(policy.getShowInDetail()),
                    policy.getPolicyVersion() == null ? 1 : policy.getPolicyVersion(),
                    EnableStatus.ENABLED.matches(policy.getWriteBarrier())
            ));
            if (!EnableStatus.ENABLED.matches(policy.getEnabled())) {
                continue;
            }
            AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, policy.getObjectId());
            if (object == null || StringUtils.isBlank(object.getConfigKey())) {
                continue;
            }
            AiCrudConfig config = resolveConfig(tenantId, object);
            if (config == null || StringUtils.isBlank(config.getTableName())) {
                continue;
            }
            tables.put(config.getTableName(), new DataAuditPolicyIndex.TableBinding(
                    object.getId(), null, config.getTableName(), "", "id", true));
            tables.put(config.getTableName().toLowerCase(Locale.ROOT), tables.get(config.getTableName()));
            addChildBindings(tables, object, config);
        }
        DataAuditTransactionHolder.index().replaceTenant(tenantId, tables, objects);
    }

    private void addChildBindings(Map<String, DataAuditPolicyIndex.TableBinding> tables,
                                  AiBusinessObject object,
                                  AiCrudConfig config) {
        try {
            JsonNode children = objectMapper.readTree(StringUtils.defaultString(config.getOptions(), "{}"))
                    .path("masterDetailConfig").path("children");
            if (!children.isArray()) {
                return;
            }
            for (JsonNode child : children) {
                String tableName = child.path("tableName").asText(null);
                if (StringUtils.isBlank(tableName)) {
                    tableName = child.path("table").asText(null);
                }
                if (StringUtils.isBlank(tableName)) {
                    continue;
                }
                String relationKey = child.path("relationKey").asText(child.path("key").asText(""));
                String fk = child.path("foreignKey").asText(child.path("childFkColumn").asText(""));
                DataAuditPolicyIndex.TableBinding binding = new DataAuditPolicyIndex.TableBinding(
                        object.getId(), object.getId(), tableName, relationKey, fk, false);
                tables.put(tableName, binding);
                tables.put(tableName.toLowerCase(Locale.ROOT), binding);
            }
        } catch (Exception ignored) {
            // 子表索引失败时仍保留主表采集
        }
    }

    public CoverageResult evaluateCoverage(AiBusinessObject object) {
        List<DataAuditCoverageItemVO> items = new ArrayList<>();
        AiCrudConfig config = object == null ? null : resolveConfig(DataAuditTenantSupport.currentTenantId(), object);
        items.add(item("OBJECT", "业务对象存在", object != null, "缺少业务对象"));
        items.add(item("CONFIG", "存在运行配置", config != null && StringUtils.isNotBlank(config.getConfigKey()),
                "对象尚未绑定运行配置"));
        items.add(item("DATASOURCE", "审计写入平台库", true,
                "审计证据写入平台库，与业务运行数据源隔离"));
        items.add(item("PRIMARY_KEY", "稳定主键", config != null && StringUtils.isNotBlank(config.getTableName()),
                "缺少物理表或主键"));
        items.add(item("TENANT", "租户隔离", true, "缺少租户隔离"));
        items.add(item("CHILD_SUMMARY", "子表行摘要", true,
                "子表只记录新增/修改/删除行数，删除时保留行主键"));
        items.add(item("RESERVED_FIELD", "无保留字段冲突", !hasReservedField(config),
                "存在名为 _dataAudit 的业务字段，请先迁移"));
        items.add(item("WRITE_PATH", "平台写入口", true, "写入口未覆盖"));
        boolean passed = items.stream().allMatch(item -> Boolean.TRUE.equals(item.getPassed()));
        return new CoverageResult(passed, items);
    }

    private boolean hasReservedField(AiCrudConfig config) {
        if (config == null || StringUtils.isBlank(config.getModelSchema())) {
            return false;
        }
        try {
            LowcodeModelSchema schema = objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
            if (schema.getFields() == null) {
                return false;
            }
            for (LowcodeFieldSchema field : schema.getFields()) {
                if (field != null && DataAuditPayloadSupport.PAYLOAD_KEY.equals(field.getField())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private AiDataAuditPolicy newPolicy(Long tenantId, Long objectId) {
        AiDataAuditPolicy policy = new AiDataAuditPolicy();
        policy.setId(IdWorker.getId());
        policy.setTenantId(tenantId);
        policy.setObjectId(objectId);
        policy.setEnabled(EnableStatus.DISABLED.getCode());
        policy.setReasonRequired(EnableStatus.ENABLED.getCode());
        policy.setShowInDetail(EnableStatus.ENABLED.getCode());
        policy.setPolicyVersion(0);
        policy.setWriteBarrier(EnableStatus.DISABLED.getCode());
        policy.setCoverageStatus("PENDING");
        policy.setCreateBy(SessionHelper.getUserId());
        policy.setCreateTime(LocalDateTime.now());
        policy.setCreateDept(SessionHelper.getMainOrgId());
        policy.setUpdateBy(SessionHelper.getUserId());
        policy.setUpdateTime(policy.getCreateTime());
        policy.setDelFlag(0L);
        return policy;
    }

    private AiBusinessObject requireObject(Long tenantId, Long objectId) {
        if (objectId == null) {
            throw DataAuditErrorCode.AUDIT_UNSUPPORTED.exception("缺少业务对象");
        }
        AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, objectId);
        if (object == null) {
            throw DataAuditErrorCode.AUDIT_FORBIDDEN.exception("业务对象不存在");
        }
        return object;
    }

    private AiCrudConfig resolveConfig(Long tenantId, AiBusinessObject object) {
        if (object == null || StringUtils.isBlank(object.getConfigKey())) {
            return null;
        }
        return crudConfigMapper.selectByConfigKey(tenantId, object.getConfigKey());
    }

    private DataAuditCoverageItemVO item(String code, String label, boolean passed, String message) {
        DataAuditCoverageItemVO item = new DataAuditCoverageItemVO();
        item.setCode(code);
        item.setLabel(label);
        item.setPassed(passed);
        item.setMessage(passed ? "通过" : message);
        return item;
    }

    private String firstFailure(CoverageResult coverage) {
        return coverage.items().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getPassed()))
                .map(DataAuditCoverageItemVO::getMessage)
                .findFirst()
                .orElse(DataAuditErrorCode.AUDIT_UNSUPPORTED.getMessage());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public record CoverageResult(boolean passed, List<DataAuditCoverageItemVO> items) {
    }
}
