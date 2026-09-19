package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
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
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.IGenDatasourceService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationFormDataVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 把页面表单自动转换为应用内部托管的数据存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessApplicationFormDataService {

    private static final String MANAGED_BY_PAGE_FORM = "PAGE_FORM";
    private static final String LOWCODE_RUNTIME = "LOWCODE_RUNTIME";
    private static final String CREATE_MODE_BLANK = "BLANK";
    private static final String CREATE_MODE_DB_IMPORT = "DB_IMPORT";
    /** 上次准备内容签名存放于对象 options 内，签名一致时短路高频保存草稿链路。 */
    private static final String PROVISION_SIGNATURE_KEY = "provisionSignature";

    private final BusinessApplicationService applicationService;
    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessObjectService objectService;
    private final BusinessObjectCreateService objectCreateService;
    private final BusinessObjectDesignerService designerService;
    private final BusinessNamingService namingService;
    private final IGenDatasourceService datasourceService;
    private final BusinessObjectTableMappingService tableMappingService;
    private final BusinessObjectMapper businessObjectMapper;
    private final PlatformTransactionManager transactionManager;

    public BusinessApplicationFormDataVO provision(
            Long applicationId, BusinessApplicationFormDataProvisionDTO request) {
        ProvisionRequest normalized = normalizeRequest(request);
        ProvisionResult provisioned = new TransactionTemplate(transactionManager).execute(
                status -> provisionMetadata(applicationId, normalized));
        if (provisioned == null) {
            throw new BusinessException("表单数据存储准备失败，请重试");
        }
        if (Boolean.TRUE.equals(provisioned.result().getUnchanged())) {
            return provisioned.result();
        }
        if (!provisioned.shouldSyncDatabase()) {
            return provisioned.result();
        }
        try {
            // 复用 provisionMetadata 中已加载的 DesignerContext，避免重复查询数据库
            if (provisioned.context() != null) {
                tableMappingService.syncManagedDatabase(
                        provisioned.context(), applicationId, normalized.formAssetId());
            } else {
                tableMappingService.syncManagedDatabase(
                        provisioned.objectId(), applicationId, normalized.formAssetId());
            }
        } catch (RuntimeException e) {
            if (CREATE_MODE_DB_IMPORT.equals(normalized.createMode())) {
                log.warn("[表单数据保存] 引用现有表时 DDL 同步失败，已降级为警告: objectId={}, formAssetId={}, error={}",
                        provisioned.objectId(), normalized.formAssetId(), e.getMessage());
                provisioned.result().setDdlWarning("数据表结构同步失败：" + StringUtils.defaultIfBlank(
                        e.getMessage(), "请在高级数据设置中确认数据库调整") + "。表单设计和选项配置已保存，不影响发布。");
                return provisioned.result();
            }
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
        boolean fromAssociation = object != null;

        if (object == null) {
            object = findReusableManagedObject(application, normalized.formAssetId());
        }
        Long objectId;
        if (object == null) {
            objectId = createManagedObject(application, normalized);
            created = true;
        } else {
            objectId = object.getId();
            // 保存草稿对每个已托管表单都会触发 provision；内容未变时短路，
            // 跳过设计器保存与后续 DDL 同步（链路中最重的两段）。
            if (fromAssociation
                    && StringUtils.equals(provisionSignature(normalized), readProvisionSignature(object))) {
                return new ProvisionResult(objectId,
                        result(normalized.formAssetId(), object, false, true));
            }
        }

        BusinessObjectDesignerService.DesignerContext designerContext = syncDesigner(objectId, normalized);
        if (!containsObject(associations, objectId)) {
            attachManagedObject(applicationId, associations, objectId, normalized.formAssetId());
        }

        AiBusinessObject saved = objectService.requireEntity(objectId);
        writeProvisionSignature(saved, provisionSignature(normalized));
        return new ProvisionResult(
                objectId,
                result(normalized.formAssetId(), saved, created, false),
                designerContext,
                !(created && CREATE_MODE_DB_IMPORT.equals(normalized.createMode())));
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
        String createMode = CREATE_MODE_DB_IMPORT.equalsIgnoreCase(StringUtils.trimToEmpty(request.getCreateMode()))
                ? CREATE_MODE_DB_IMPORT
                : CREATE_MODE_BLANK;
        String importTableName = StringUtils.trimToNull(request.getImportTableName());
        if (CREATE_MODE_DB_IMPORT.equals(createMode) && importTableName == null) {
            throw new BusinessException("请选择要引用的数据表");
        }
        return new ProvisionRequest(
                formAssetId,
                formName,
                fields,
                request.getFormDesignerSchema(),
                request.getRuntimeDatasourceId(),
                createMode,
                request.getImportDatasourceId(),
                importTableName);
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
        boolean dbImport = CREATE_MODE_DB_IMPORT.equals(request.createMode());
        GenDatasource datasource = resolveRuntimeDatasource(request.runtimeDatasourceId(), !dbImport);
        String objectCode = buildManagedObjectCode(application, request);
        BusinessObjectDTO dto = new BusinessObjectDTO();
        dto.setSuiteCode(application.getSuiteCode());
        dto.setObjectCode(objectCode);
        dto.setObjectName(request.formName());
        dto.setObjectType("MASTER");
        dto.setCreateMode(dbImport ? CREATE_MODE_DB_IMPORT : CREATE_MODE_BLANK);
        dto.setRuntimeDatasourceId(datasource.getDatasourceId());
        if (dbImport) {
            dto.setImportDatasourceId(request.importDatasourceId() == null
                    ? datasource.getDatasourceId()
                    : request.importDatasourceId());
            dto.setImportTableName(request.importTableName());
        }
        dto.setModelCode(namingService.buildModelCode(application.getSuiteCode(), objectCode));
        dto.setDisplayField(request.fields().get(0).getFieldCode());
        dto.setDescription("由应用“" + application.getApplicationName() + "”中的表单自动管理");
        dto.setStatus(EnableStatus.ENABLED.getCode());
        dto.setOptions(buildObjectOptions(application, request, datasource));
        return objectCreateService.create(dto);
    }

    private GenDatasource resolveRuntimeDatasource(Long requestedId, boolean requireDdl) {
        List<GenDatasource> enabled = safeList(datasourceService.selectEnabledDatasources(LOWCODE_RUNTIME));
        if (requestedId != null) {
            GenDatasource selected = enabled.stream()
                    .filter(item -> requestedId.equals(item.getDatasourceId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("请选择有效的低代码运行数据源"));
            if (!isRuntimeWritable(selected)) {
                throw new BusinessException("当前数据源不允许写入业务数据");
            }
            if (requireDdl && !isWritableRuntimeDatasource(selected)) {
                throw new BusinessException("当前数据存储未允许自动建表，请在高级数据设置中开启自动建表");
            }
            return selected;
        }
        return enabled.stream()
                .filter(requireDdl ? this::isWritableRuntimeDatasource : this::isRuntimeWritable)
                .min(Comparator
                        .comparingInt((GenDatasource datasource) -> Integer.valueOf(1).equals(datasource.getIsDefault()) ? 0 : 1)
                        .thenComparing(datasource -> datasource.getSort() == null ? Integer.MAX_VALUE : datasource.getSort())
                        .thenComparing(datasource -> datasource.getDatasourceId() == null ? Long.MAX_VALUE : datasource.getDatasourceId()))
                .orElseThrow(() -> new BusinessException(requireDdl
                        ? "当前数据存储未允许自动建表，请在高级数据设置中开启自动建表"
                        : "请选择有效的低代码运行数据源"));
    }

    private boolean isWritableRuntimeDatasource(GenDatasource datasource) {
        return isRuntimeWritable(datasource)
                && Integer.valueOf(1).equals(datasource.getAllowRuntimeDdl());
    }

    private boolean isRuntimeWritable(GenDatasource datasource) {
        return datasource != null
                && datasource.getDatasourceId() != null
                && Integer.valueOf(1).equals(datasource.getAllowRuntimeWrite())
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
            AiBusinessApplication application, ProvisionRequest request, GenDatasource datasource) {
        boolean dbImport = CREATE_MODE_DB_IMPORT.equals(request.createMode());
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
        runtimeDatasource.put("tableMode", dbImport ? "EXISTING" : "CREATE");

        JSONObject options = managedMarker(application.getId(), request.formAssetId());
        options.put("createMode", dbImport ? CREATE_MODE_DB_IMPORT : CREATE_MODE_BLANK);
        options.put("runtimeDatasourceId", datasource.getDatasourceId());
        options.put("runtimeDatasource", runtimeDatasource);
        if (dbImport) {
            JSONObject sourceTable = new JSONObject();
            sourceTable.put("datasourceId", request.importDatasourceId() == null
                    ? datasource.getDatasourceId()
                    : request.importDatasourceId());
            sourceTable.put("tableName", request.importTableName());
            options.put("sourceTable", sourceTable);
        }
        return options.toJSONString();
    }

    private BusinessObjectDesignerService.DesignerContext syncDesigner(Long objectId, ProvisionRequest request) {
        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        designer.setObjectId(objectId);
        designer.setObjectName(request.formName());
        designer.setDisplayField(request.fields().get(0).getFieldCode());
        designer.setFields(request.fields());
        designer.setFormDesignerSchema(request.formDesignerSchema());
        return designerService.saveDesigner(objectId, designer);
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
        return result(formAssetId, object, created, false);
    }

    private BusinessApplicationFormDataVO result(
            String formAssetId, AiBusinessObject object, boolean created, boolean unchanged) {
        BusinessApplicationFormDataVO result = new BusinessApplicationFormDataVO();
        result.setFormAssetId(formAssetId);
        result.setObjectId(object.getId());
        result.setObjectCode(object.getObjectCode());
        result.setObjectName(object.getObjectName());
        result.setConfigKey(object.getConfigKey());
        result.setCreated(created);
        result.setUnchanged(unchanged);
        return result;
    }

    /**
     * 内容签名：表单名 + 字段列表 + 表单设计器 schema 的规范化 JSON 摘要。
     * 保存草稿高频重放相同内容，签名一致即可确定设计器与数据表都无需重跑。
     */
    private String provisionSignature(ProvisionRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("formName", request.formName());
        payload.put("fields", request.fields());
        payload.put("formDesignerSchema", request.formDesignerSchema());
        String canonical = payload.toJSONString(JSONWriter.Feature.SortMapEntriesByKeys);
        return sha256Hex(canonical);
    }

    private String readProvisionSignature(AiBusinessObject object) {
        return StringUtils.trimToNull(parseOptions(object.getOptions()).getString(PROVISION_SIGNATURE_KEY));
    }

    private void writeProvisionSignature(AiBusinessObject object, String signature) {
        JSONObject options = parseOptions(object.getOptions());
        options.put(PROVISION_SIGNATURE_KEY, signature);
        object.setOptions(options.toJSONString());
        businessObjectMapper.updateById(object);
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要不可用", e);
        }
    }

    private record ProvisionRequest(
            String formAssetId,
            String formName,
            List<BusinessFieldDTO> fields,
            com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO formDesignerSchema,
            Long runtimeDatasourceId,
            String createMode,
            Long importDatasourceId,
            String importTableName) {
    }

    private record ProvisionResult(Long objectId, BusinessApplicationFormDataVO result,
                                    BusinessObjectDesignerService.DesignerContext context,
                                    boolean shouldSyncDatabase) {
        ProvisionResult(Long objectId, BusinessApplicationFormDataVO result) {
            this(objectId, result, null, true);
        }

        ProvisionResult(Long objectId, BusinessApplicationFormDataVO result,
                        BusinessObjectDesignerService.DesignerContext context) {
            this(objectId, result, context, true);
        }
    }
}
