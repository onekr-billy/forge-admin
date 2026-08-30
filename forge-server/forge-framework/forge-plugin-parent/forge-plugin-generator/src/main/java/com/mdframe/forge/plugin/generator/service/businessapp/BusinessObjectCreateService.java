package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelImportRequest;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDomainService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeModelImportService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 业务对象创建编排服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessObjectCreateService {

    private static final String CREATE_MODE_DB_IMPORT = "DB_IMPORT";
    private static final String GENERAL_DOMAIN_CODE = "general";
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Set<String> DISPLAY_FIELD_KEYWORDS = Set.of("name", "title", "code", "no", "number");

    private final BusinessObjectService objectService;
    private final BusinessObjectDesignerService designerService;
    private final LowcodeDomainService domainService;
    private final LowcodeModelImportService modelImportService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(BusinessObjectDTO dto) {
        Long objectId = objectService.create(dto);
        if (isDbImport(dto)) {
            importDbTableDesign(objectId, dto);
        }
        return objectId;
    }

    private boolean isDbImport(BusinessObjectDTO dto) {
        return dto != null && CREATE_MODE_DB_IMPORT.equalsIgnoreCase(StringUtils.trimToEmpty(dto.getCreateMode()));
    }

    private void importDbTableDesign(Long objectId, BusinessObjectDTO dto) {
        Long datasourceId = dto.getImportDatasourceId();
        String tableName = StringUtils.trimToNull(dto.getImportTableName());
        if (datasourceId == null) {
            throw new BusinessException("请选择导入数据源");
        }
        if (StringUtils.isBlank(tableName) || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new BusinessException("请选择有效的数据表");
        }

        AiBusinessObject object = objectService.requireEntity(objectId);
        LowcodeModelSchema modelSchema = modelImportService.previewDbTableModel(buildImportRequest(object, datasourceId, tableName));
        if (StringUtils.isBlank(object.getDisplayField())) {
            object.setDisplayField(resolveDisplayField(modelSchema));
        }
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        context.setObject(object);
        context.setModelSchema(modelSchema);
        context.setPageSchema(null);
        designerService.saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());
    }

    private LowcodeModelImportRequest buildImportRequest(AiBusinessObject object, Long datasourceId, String tableName) {
        LowcodeModelImportRequest request = new LowcodeModelImportRequest();
        request.setDatasourceId(datasourceId);
        request.setDomainId(resolveImportDomainId(object.getSuiteCode()));
        request.setTableName(tableName);
        request.setModelCode(StringUtils.defaultIfBlank(object.getModelCode(), object.getObjectCode()));
        request.setModelName(object.getObjectName());
        request.setModelDesc(object.getDescription());
        request.setTenantEnabled(true);
        request.setMasterData("MASTER".equalsIgnoreCase(object.getObjectType()));
        return request;
    }

    private Long resolveImportDomainId(String suiteCode) {
        AiLowcodeDomain domain = domainService.getByCode(StringUtils.trimToEmpty(suiteCode));
        if (domain == null) {
            domain = domainService.getByCode(StringUtils.trimToEmpty(suiteCode).toLowerCase(Locale.ROOT));
        }
        if (domain == null) {
            domain = domainService.getByCode(GENERAL_DOMAIN_CODE);
        }
        if (domain == null) {
            throw new BusinessException("缺少可用的低代码业务领域，无法导入数据库表结构");
        }
        return domain.getId();
    }

    private String resolveDisplayField(LowcodeModelSchema modelSchema) {
        if (modelSchema == null || modelSchema.getFields() == null || modelSchema.getFields().isEmpty()) {
            return null;
        }
        return modelSchema.getFields().stream()
                .filter(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()))
                .min(Comparator.comparingInt(this::displayFieldPriority))
                .map(LowcodeFieldSchema::getField)
                .orElse(null);
    }

    private int displayFieldPriority(LowcodeFieldSchema field) {
        String fieldName = StringUtils.defaultString(field.getField()).toLowerCase(Locale.ROOT);
        String columnName = StringUtils.defaultString(field.getColumnName()).toLowerCase(Locale.ROOT);
        String label = StringUtils.defaultString(field.getLabel()).toLowerCase(Locale.ROOT);
        String joined = fieldName + " " + columnName + " " + label;
        for (String keyword : DISPLAY_FIELD_KEYWORDS) {
            if (joined.contains(keyword)) {
                return 0;
            }
        }
        return 1;
    }
}
