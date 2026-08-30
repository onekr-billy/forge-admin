package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.GenDatasource;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationFormDataProvisionDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectQueryDTO;
import com.mdframe.forge.plugin.generator.service.IGenDatasourceService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationFormDataVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 把页面表单自动转换为应用内部托管的数据存储。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationFormDataService {

    private static final String MANAGED_BY_PAGE_FORM = "PAGE_FORM";
    private static final String LOWCODE_RUNTIME = "LOWCODE_RUNTIME";

    private final BusinessApplicationService applicationService;
    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessObjectService objectService;
    private final BusinessObjectCreateService objectCreateService;
    private final BusinessObjectDesignerService designerService;
    private final BusinessNamingService namingService;
    private final IGenDatasourceService datasourceService;
    private final BusinessObjectTableMappingService tableMappingService;
    private final PlatformTransactionManager transactionManager;

    public BusinessApplicationFormDataVO provision(
            Long applicationId, BusinessApplicationFormDataProvisionDTO request) {
        ProvisionRequest normalized = normalizeRequest(request);
        ProvisionResult provisioned = new TransactionTemplate(transactionManager).execute(
                status -> provisionMetadata(applicationId, normalized));
        if (provisioned == null) {
            throw new BusinessException("表单数据存储准备失败，请重试");
        }
        try {
            tableMappingService.syncManagedDatabase(
                    provisioned.objectId(), applicationId, normalized.formAssetId());
        } catch (RuntimeException e) {
            throw databaseSyncFailure(e);
        }
        return provisioned.result();
    }

    /**
     * 发布前重新同步当前应用自己托管的页面表单数据表。
     *
     * <p>关联标记和对象标记会在两层服务中分别校验。普通对象、导入表和其它
     * 应用的托管对象不会进入自动 DDL 通道。</p>
     *
     * @return 实际执行同步检查的托管数据表数量
     */
    public int synchronizeManagedDatabases(Long applicationId) {
        int synchronizedCount = 0;
        for (BusinessApplicationObjectVO association : safeAssociations(
                applicationObjectService.list(applicationId))) {
            JSONObject marker = parseOptions(association.getOptions());
            String formAssetId = StringUtils.trimToNull(marker.getString("sourceFormAssetId"));
            if (association.getObjectId() == null
                    || !MANAGED_BY_PAGE_FORM.equals(marker.getString("managedBy"))
                    || !StringUtils.equals(String.valueOf(applicationId),
                            marker.getString("sourceApplicationId"))
                    || formAssetId == null) {
                continue;
            }
            if ("IN_SYNC".equalsIgnoreCase(StringUtils.defaultString(association.getSyncStatus()))) {
                continue;
            }
            tableMappingService.syncManagedDatabase(
                    association.getObjectId(), applicationId, formAssetId);
            synchronizedCount++;
        }
        return synchronizedCount;
    }

    /**
     * 在独立元数据事务中创建或更新托管对象。
     *
     * <p>事务提交后才允许执行 MySQL DDL，避免把隐式提交的 DDL 伪装成可回滚事务。</p>
     */
    private ProvisionResult provisionMetadata(Long applicationId, ProvisionRequest normalized) {
        AiBusinessApplication application = applicationService.requireEntity(applicationId);
        List<BusinessApplicationObjectVO> associations = applicationObjectService.list(applicationId);
        AiBusinessObject object = resolveAssociatedManagedObject(associations, normalized.formAssetId());
        boolean created = false;

        if (object == null) {
            object = findReusableManagedObject(application, normalized.formAssetId());
        }
        Long objectId;
        if (object == null) {
            objectId = createManagedObject(application, normalized);
            created = true;
        } else {
            objectId = object.getId();
        }

        syncDesigner(objectId, normalized);
        if (!containsObject(associations, objectId)) {
            attachManagedObject(applicationId, associations, objectId, normalized.formAssetId());
        }

        AiBusinessObject saved = objectService.requireEntity(objectId);
        return new ProvisionResult(objectId, result(normalized.formAssetId(), saved, created));
    }

    private ProvisionRequest normalizeRequest(BusinessApplicationFormDataProvisionDTO request) {
        if (request == null) {
            throw new BusinessException("表单数据不能为空");
        }
        String formAssetId = StringUtils.trimToNull(request.getFormAssetId());
        if (formAssetId == null || formAssetId.length() > 128) {
            throw new BusinessException("表单标识不正确");
        }
        List<BusinessFieldDTO> fields = request.getFields() == null
                ? List.of()
                : request.getFields().stream()
                        .filter(field -> field != null
                                && !Boolean.TRUE.equals(field.getSystemField())
                                && StringUtils.isNotBlank(field.getFieldCode()))
                        .toList();
        if (fields.isEmpty()) {
            throw new BusinessException("表单还没有可保存的数据字段");
        }
        String formName = StringUtils.defaultIfBlank(request.getFormName(), "未命名表单").trim();
        return new ProvisionRequest(formAssetId, formName, fields, request.getFormDesignerSchema());
    }

    private AiBusinessObject resolveAssociatedManagedObject(
            List<BusinessApplicationObjectVO> associations, String formAssetId) {
        return safeAssociations(associations).stream()
                .filter(association -> isManagedForForm(association.getOptions(), formAssetId))
                .map(BusinessApplicationObjectVO::getObjectId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(objectService::requireEntity)
                .orElse(null);
    }

    private AiBusinessObject findReusableManagedObject(
            AiBusinessApplication application, String formAssetId) {
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setSuiteCode(application.getSuiteCode());
        List<BusinessObjectVO> candidates = safeList(objectService.list(query)).stream()
                .filter(object -> isManagedObject(object.getOptions(), application.getId(), formAssetId))
                .toList();
        if (candidates.size() > 1) {
            throw new BusinessException("表单存在多个历史数据存储，请在高级数据设置中处理");
        }
        return candidates.isEmpty() ? null : objectService.requireEntity(candidates.get(0).getId());
    }

    private Long createManagedObject(
            AiBusinessApplication application, ProvisionRequest request) {
        GenDatasource datasource = resolveRuntimeDatasource();
        String objectCode = buildManagedObjectCode(application, request);
        BusinessObjectDTO dto = new BusinessObjectDTO();
        dto.setSuiteCode(application.getSuiteCode());
        dto.setObjectCode(objectCode);
        dto.setObjectName(request.formName());
        dto.setObjectType("MASTER");
        dto.setCreateMode("BLANK");
        dto.setRuntimeDatasourceId(datasource.getDatasourceId());
        dto.setModelCode(namingService.buildModelCode(application.getSuiteCode(), objectCode));
        dto.setDisplayField(request.fields().get(0).getFieldCode());
        dto.setDescription("由应用“" + application.getApplicationName() + "”中的表单自动管理");
        dto.setStatus(EnableStatus.ENABLED.getCode());
        dto.setOptions(buildObjectOptions(application, request.formAssetId(), datasource));
        return objectCreateService.create(dto);
    }

    private GenDatasource resolveRuntimeDatasource() {
        return safeList(datasourceService.selectEnabledDatasources(LOWCODE_RUNTIME)).stream()
                .filter(this::isWritableRuntimeDatasource)
                .min(Comparator
                        .comparingInt((GenDatasource datasource) -> Integer.valueOf(1).equals(datasource.getIsDefault()) ? 0 : 1)
                        .thenComparing(datasource -> datasource.getSort() == null ? Integer.MAX_VALUE : datasource.getSort())
                        .thenComparing(datasource -> datasource.getDatasourceId() == null ? Long.MAX_VALUE : datasource.getDatasourceId()))
                .orElseThrow(() -> new BusinessException(
                        "当前数据存储未允许自动建表，请在高级数据设置中开启自动建表"));
    }

    private boolean isWritableRuntimeDatasource(GenDatasource datasource) {
        return datasource != null
                && datasource.getDatasourceId() != null
                && Integer.valueOf(1).equals(datasource.getAllowRuntimeWrite())
                && Integer.valueOf(1).equals(datasource.getAllowRuntimeDdl())
                && !Integer.valueOf(1).equals(datasource.getReadonly());
    }

    private BusinessException databaseSyncFailure(RuntimeException error) {
        String detail = StringUtils.trimToNull(error.getMessage());
        if (detail == null) {
            detail = "目标数据存储暂时不可用";
        }
        return new BusinessException(
                "数据表创建失败：" + detail + "；已保留表单设计，可直接重试", error);
    }

    private String buildManagedObjectCode(
            AiBusinessApplication application, ProvisionRequest request) {
        String base = namingService.normalizeObjectCode(
                application.getApplicationCode() + "_" + request.formName(), request.formName());
        String identity = application.getId() + ":" + request.formAssetId();
        String suffix = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 8);
        String prefix = StringUtils.left(base, 48 - suffix.length() - 1).replaceAll("_+$", "");
        return prefix + "_" + suffix;
    }

    private String buildObjectOptions(
            AiBusinessApplication application, String formAssetId, GenDatasource datasource) {
        JSONObject runtimeDatasource = new JSONObject();
        runtimeDatasource.put("datasourceId", datasource.getDatasourceId());
        runtimeDatasource.put("datasourceCode", datasource.getDatasourceCode());
        runtimeDatasource.put("datasourceName", datasource.getDatasourceName());
        runtimeDatasource.put("dbType", datasource.getDbType());
        runtimeDatasource.put("usageScope", datasource.getUsageScope());
        runtimeDatasource.put("allowWrite", Integer.valueOf(1).equals(datasource.getAllowRuntimeWrite()));
        runtimeDatasource.put("allowDdl", Integer.valueOf(1).equals(datasource.getAllowRuntimeDdl()));
        runtimeDatasource.put("readonly", Integer.valueOf(1).equals(datasource.getReadonly()));
        runtimeDatasource.put("riskLevel", datasource.getRiskLevel());
        runtimeDatasource.put("tableMode", "CREATE");

        JSONObject options = managedMarker(application.getId(), formAssetId);
        options.put("createMode", "BLANK");
        options.put("runtimeDatasourceId", datasource.getDatasourceId());
        options.put("runtimeDatasource", runtimeDatasource);
        return options.toJSONString();
    }

    private void syncDesigner(Long objectId, ProvisionRequest request) {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        designer.setObjectId(objectId);
        designer.setObjectName(request.formName());
        designer.setDisplayField(request.fields().get(0).getFieldCode());
        designer.setFields(request.fields());
        designer.setFormDesignerSchema(request.formDesignerSchema());
        designerService.saveDesigner(objectId, designer);
    }

    private void attachManagedObject(
            Long applicationId,
            List<BusinessApplicationObjectVO> current,
            Long objectId,
            String formAssetId) {
        List<BusinessApplicationObjectDTO> next = new ArrayList<>();
        safeAssociations(current).forEach((association) -> {
            BusinessApplicationObjectDTO item = new BusinessApplicationObjectDTO();
            item.setObjectId(association.getObjectId());
            item.setObjectRole(association.getObjectRole());
            item.setSortOrder(association.getSortOrder());
            item.setOptions(association.getOptions());
            next.add(item);
        });
        BusinessApplicationObjectDTO managed = new BusinessApplicationObjectDTO();
        managed.setObjectId(objectId);
        managed.setObjectRole(next.isEmpty()
                ? BusinessApplicationObjectRole.PRIMARY
                : BusinessApplicationObjectRole.SHARED);
        managed.setSortOrder(next.size());
        managed.setOptions(managedMarker(applicationId, formAssetId).toJSONString());
        next.add(managed);
        applicationObjectService.replace(applicationId, next);
    }

    private JSONObject managedMarker(Long applicationId, String formAssetId) {
        JSONObject marker = new JSONObject();
        marker.put("managedBy", MANAGED_BY_PAGE_FORM);
        marker.put("sourceApplicationId", applicationId);
        marker.put("sourceFormAssetId", formAssetId);
        marker.put("hiddenFromPrimaryFlow", true);
        return marker;
    }

    private boolean isManagedForForm(String options, String formAssetId) {
        JSONObject marker = parseOptions(options);
        return MANAGED_BY_PAGE_FORM.equals(marker.getString("managedBy"))
                && StringUtils.equals(formAssetId, marker.getString("sourceFormAssetId"));
    }

    private boolean isManagedObject(String options, Long applicationId, String formAssetId) {
        JSONObject marker = parseOptions(options);
        return MANAGED_BY_PAGE_FORM.equals(marker.getString("managedBy"))
                && StringUtils.equals(String.valueOf(applicationId), marker.getString("sourceApplicationId"))
                && StringUtils.equals(formAssetId, marker.getString("sourceFormAssetId"));
    }

    private JSONObject parseOptions(String options) {
        try {
            JSONObject result = JSON.parseObject(options);
            return result == null ? new JSONObject() : result;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private boolean containsObject(List<BusinessApplicationObjectVO> associations, Long objectId) {
        return safeAssociations(associations).stream()
                .anyMatch(item -> java.util.Objects.equals(item.getObjectId(), objectId));
    }

    private List<BusinessApplicationObjectVO> safeAssociations(List<BusinessApplicationObjectVO> associations) {
        return safeList(associations);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private BusinessApplicationFormDataVO result(
            String formAssetId, AiBusinessObject object, boolean created) {
        BusinessApplicationFormDataVO result = new BusinessApplicationFormDataVO();
        result.setFormAssetId(formAssetId);
        result.setObjectId(object.getId());
        result.setObjectCode(object.getObjectCode());
        result.setObjectName(object.getObjectName());
        result.setConfigKey(object.getConfigKey());
        result.setCreated(created);
        return result;
    }

    private record ProvisionRequest(
            String formAssetId,
            String formName,
            List<BusinessFieldDTO> fields,
            com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO formDesignerSchema) {
    }

    private record ProvisionResult(Long objectId, BusinessApplicationFormDataVO result) {
    }
}
