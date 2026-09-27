package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFieldVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 平台托管的低代码审批流程状态字段。 */
@Service
@RequiredArgsConstructor
public class BusinessFlowStatusFieldService {

    public static final String FIELD_CODE = "flowStatus";
    public static final String COLUMN_NAME = "flow_status";
    public static final String DICT_TYPE = "business_flow_status";
    public static final String DDL_PERMISSION = "ai:lowcode:deploy-ddl";
    private static final Set<String> TEXT_TYPES = Set.of("varchar", "char", "text");

    private final BusinessObjectDesignerService designerService;
    private final BusinessFieldDesignService fieldDesignService;
    private final LowcodeDdlService ddlService;

    @Transactional(rollbackFor = Exception.class)
    public BusinessFieldVO ensure(Long objectId) {
        if (!hasDdlPermission()) {
            throw new BusinessException("缺少同步数据库权限: " + DDL_PERMISSION);
        }
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        LowcodeFieldSchema existing = findFlowStatusField(context);
        boolean fieldCreated = existing == null;
        if (existing == null) {
            fieldDesignService.addField(objectId, createField());
            context = designerService.loadContext(objectId);
            existing = findFlowStatusField(context);
        }
        validateCompatible(existing);
        boolean metadataChanged = normalizeManagedMetadata(existing);
        if (metadataChanged) {
            designerService.saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());
            context = designerService.loadContext(objectId);
            existing = findFlowStatusField(context);
            validateCompatible(existing);
        }
        ddlService.executeAdditiveColumn(context.getModelSchema(), COLUMN_NAME);
        // 字段已存在时仍要把列表选列补齐：旧 listGridLayout 快照常缺 flowStatus。
        boolean listVisibilityChanged = fieldDesignService.ensureFieldListVisibility(objectId, FIELD_CODE);
        markPublishedObjectChanged(objectId, fieldCreated || metadataChanged || listVisibilityChanged);
        return fieldDesignService.listFields(objectId).stream()
                .filter(field -> FIELD_CODE.equals(field.getFieldCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("流程状态字段创建后无法读取，请刷新后重试"));
    }

    private BusinessFieldDTO createField() {
        BusinessFieldDTO field = new BusinessFieldDTO();
        field.setFieldName("流程状态");
        field.setFieldCode(FIELD_CODE);
        field.setColumnName(COLUMN_NAME);
        field.setFieldType("DICT");
        field.setDataType("varchar");
        field.setLength(32);
        field.setRequired(true);
        field.setDefaultValue("DRAFT");
        field.setSearchable(true);
        field.setListVisible(true);
        field.setFormVisible(true);
        field.setImportable(false);
        field.setExportable(true);
        field.setComponentType("select");
        field.setQueryType("eq");
        field.setDictType(DICT_TYPE);
        field.setReadonly(true);
        field.setFieldStatus("ENABLED");
        field.setRemark("平台托管的审批流程状态，独立于业务状态");
        Map<String, Object> advancedProps = new LinkedHashMap<>();
        advancedProps.put("managedBy", "BUSINESS_FLOW");
        advancedProps.put("managedField", true);
        advancedProps.put("allowedValues",
                Set.of("DRAFT", "IN_PROCESS", "NEED_MODIFY", "APPROVED", "REJECTED", "CANCELED"));
        field.setAdvancedProps(advancedProps);
        return field;
    }

    private LowcodeFieldSchema findFlowStatusField(BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getModelSchema() == null || context.getModelSchema().getFields() == null) {
            return null;
        }
        return context.getModelSchema().getFields().stream()
                .filter(field -> field != null && (FIELD_CODE.equals(field.getField()) || COLUMN_NAME.equals(field.getColumnName())))
                .findFirst()
                .orElse(null);
    }

    private void validateCompatible(LowcodeFieldSchema field) {
        if (field == null || !FIELD_CODE.equals(field.getField()) || !COLUMN_NAME.equals(field.getColumnName())) {
            throw new BusinessException("已有流程状态字段编码或数据库列冲突，请将其调整为 flowStatus / flow_status");
        }
        String dataType = StringUtils.defaultString(field.getDataType()).toLowerCase(Locale.ROOT);
        if (!TEXT_TYPES.contains(dataType) || field.getLength() != null && field.getLength() < 16) {
            throw new BusinessException("已有 flowStatus 字段类型不兼容，需要 varchar(16) 以上文本类型");
        }
        if (StringUtils.isNotBlank(field.getDictType()) && !DICT_TYPE.equals(field.getDictType())) {
            throw new BusinessException("已有 flowStatus 字段使用了其他字典，请改为 " + DICT_TYPE);
        }
    }

    /**
     * 旧版本创建的 flowStatus 可能只有字段名/列名，没有平台托管标识；
     * 统一补齐元数据后，发布快照和前端运行时才能稳定识别该字段。
     */
    private boolean normalizeManagedMetadata(LowcodeFieldSchema field) {
        if (field == null) {
            return false;
        }
        boolean changed = false;
        Map<String, Object> advancedProps = field.getAdvancedProps() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(field.getAdvancedProps());
        if (!"BUSINESS_FLOW".equals(String.valueOf(advancedProps.get("managedBy")))) {
            advancedProps.put("managedBy", "BUSINESS_FLOW");
            changed = true;
        }
        if (!Boolean.TRUE.equals(advancedProps.get("managedField"))) {
            advancedProps.put("managedField", true);
            changed = true;
        }
        if (StringUtils.isBlank(field.getDictType())) {
            field.setDictType(DICT_TYPE);
            changed = true;
        }
        if (StringUtils.isBlank(field.getFieldStatus())) {
            field.setFieldStatus("ENABLED");
            changed = true;
        }
        if (field.getListVisible() == null) {
            field.setListVisible(true);
            changed = true;
        }
        if (changed) {
            field.setAdvancedProps(advancedProps);
        }
        return changed;
    }

    /**
     * 确保已发布对象在流程字段被重新确认后进入可重新发布状态；
     * 否则应用发布器会把对象误判为无变更并复用旧版本快照。
     */
    private void markPublishedObjectChanged(Long objectId, boolean repaired) {
        BusinessObjectDesignerService.DesignerContext latest = designerService.loadContext(objectId);
        if (latest == null || latest.getObject() == null || latest.getConfig() == null) {
            return;
        }
        Integer draftVersion = latest.getConfig().getDraftVersion();
        Integer publishedVersion = latest.getConfig().getPublishedVersion();
        boolean draftAheadOfPublished = draftVersion != null && publishedVersion != null
                && draftVersion > publishedVersion;
        if (BusinessObjectDesignStatus.PUBLISHED.matches(latest.getObject().getDesignStatus())
                && "PUBLISHED".equals(latest.getConfig().getPublishStatus())
                && (repaired || draftAheadOfPublished)) {
            designerService.saveDraft(latest, BusinessObjectDesignStatus.CHANGED.getCode());
        }
    }

    private boolean hasDdlPermission() {
        try {
            return SessionHelper.hasPermission(DDL_PERMISSION);
        } catch (Exception ignored) {
            return false;
        }
    }
}
