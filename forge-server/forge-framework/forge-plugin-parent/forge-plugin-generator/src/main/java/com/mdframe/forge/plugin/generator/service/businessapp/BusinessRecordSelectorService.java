package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessRecordSelectorQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessRecordSelectorResultVO;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 通用记录选择器服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessRecordSelectorService {

    static final String OR_LIKE_SEARCH_KEY = "__orLike";

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time",
            "delFlag", "del_flag"
    );

    private final BusinessObjectMapper businessObjectMapper;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessPermissionService permissionService;

    public BusinessRecordSelectorResultVO query(BusinessRecordSelectorQueryDTO dto, PageQuery pageQuery) {
        BusinessRecordSelectorQueryDTO query = dto == null ? new BusinessRecordSelectorQueryDTO() : dto;
        String objectCode = resolveObjectCode(query);
        query.setObjectCode(objectCode);
        if (StringUtils.isBlank(objectCode)) {
            throw new BusinessException("选择器缺少业务对象编码: " + describeObjectCodeFields(query));
        }
        AiBusinessObject object = resolveObject(query);
        validateObjectViewPermission(object);
        if (StringUtils.isBlank(object.getConfigKey())) {
            throw new BusinessException("业务对象未发布运行配置: " + object.getObjectCode());
        }
        PageQuery effectivePage = pageQuery == null ? new PageQuery() : pageQuery;
        Set<String> requestedFields = resolveRequestedFields(query);
        effectivePage.setOrderByColumn(null);
        effectivePage.setIsAsc(null);
        String orderByColumn = normalizeField(query.getOrderByColumn());
        if (StringUtils.isNotBlank(orderByColumn) && requestedFields.contains(orderByColumn)) {
            effectivePage.setOrderByColumn(orderByColumn);
            effectivePage.setIsAsc(StringUtils.defaultIfBlank(query.getIsAsc(), "asc"));
        }
        DynamicCrudQuery dynamicQuery = new DynamicCrudQuery();
        dynamicQuery.setSearchParams(buildSearchParams(query));
        Page<Map<String, Object>> page = dynamicCrudService.selectPage(object.getConfigKey(), effectivePage, dynamicQuery);

        List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns = resolveColumns(query, requestedFields);
        Set<String> allowedFields = resolveAllowedFields(query, columns);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(row -> normalizeRecord(row, columns, allowedFields))
                .toList();

        BusinessRecordSelectorResultVO result = new BusinessRecordSelectorResultVO();
        result.setSuiteCode(object.getSuiteCode());
        result.setObjectCode(object.getObjectCode());
        result.setObjectName(object.getObjectName());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setColumns(columns);
        result.setRecords(records);
        result.setFieldMappings(resolveFieldMappings(query));
        return result;
    }

    String resolveObjectCode(BusinessRecordSelectorQueryDTO query) {
        if (query == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getObjectCode()),
                StringUtils.trimToNull(query.getBusinessObjectCode()),
                StringUtils.trimToNull(query.getTargetObjectCode()),
                StringUtils.trimToNull(query.getTargetEntityCode()),
                StringUtils.trimToNull(query.getCandidateObjectCode()),
                StringUtils.trimToNull(query.getReferenceObjectCode()),
                StringUtils.trimToNull(query.getRefObjectCode()),
                StringUtils.trimToNull(query.getSourceObjectCode()),
                StringUtils.trimToNull(query.getTargetCode()));
    }

    private String describeObjectCodeFields(BusinessRecordSelectorQueryDTO query) {
        if (query == null) {
            return "query=null";
        }
        return "objectCode=" + StringUtils.defaultString(query.getObjectCode())
                + ", businessObjectCode=" + StringUtils.defaultString(query.getBusinessObjectCode())
                + ", targetObjectCode=" + StringUtils.defaultString(query.getTargetObjectCode())
                + ", targetEntityCode=" + StringUtils.defaultString(query.getTargetEntityCode())
                + ", candidateObjectCode=" + StringUtils.defaultString(query.getCandidateObjectCode())
                + ", referenceObjectCode=" + StringUtils.defaultString(query.getReferenceObjectCode())
                + ", refObjectCode=" + StringUtils.defaultString(query.getRefObjectCode())
                + ", sourceObjectCode=" + StringUtils.defaultString(query.getSourceObjectCode())
                + ", targetCode=" + StringUtils.defaultString(query.getTargetCode());
    }

    private AiBusinessObject resolveObject(BusinessRecordSelectorQueryDTO query) {
        AiBusinessObject object = StringUtils.isNotBlank(query.getSuiteCode())
                ? businessObjectMapper.selectByObjectCode(resolveTenantId(), query.getSuiteCode().trim(), query.getObjectCode().trim())
                : businessObjectMapper.selectFirstByObjectCode(resolveTenantId(), query.getObjectCode().trim());
        if (object == null) {
            throw new BusinessException("业务对象不存在: " + query.getObjectCode());
        }
        if (EnableStatus.DISABLED.matches(object.getStatus())) {
            throw new BusinessException("业务对象已停用: " + object.getObjectName());
        }
        return object;
    }

    private void validateObjectViewPermission(AiBusinessObject object) {
        if (object == null || StringUtils.isBlank(object.getObjectCode())) {
            throw new BusinessException("业务对象不存在");
        }
        if (!permissionService.hasDocumentActionPermission(object.getObjectCode(), "VIEW")) {
            throw new BusinessException("无权限查询选择器对象: " + object.getObjectName());
        }
    }

    private Map<String, Object> buildSearchParams(BusinessRecordSelectorQueryDTO query) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (query.getSearchParams() != null) {
            query.getSearchParams().forEach((key, value) -> {
                String field = normalizeField(key);
                if (StringUtils.isNotBlank(field) && !isInternalField(field) && !isEmpty(value)) {
                    params.put(field, value);
                }
            });
        }
        String keyword = StringUtils.trimToNull(query.getKeyword());
        List<String> keywordFields = normalizeKeywordFields(query.getKeywordFields());
        if (keyword != null && keywordFields.size() == 1) {
            params.put(keywordFields.get(0), keyword);
        } else if (keyword != null && keywordFields.size() > 1) {
            List<Map<String, Object>> orLikeConditions = keywordFields.stream()
                    .map(field -> {
                        Map<String, Object> condition = new LinkedHashMap<>();
                        condition.put("field", field);
                        condition.put("value", keyword);
                        return condition;
                    })
                    .toList();
            params.put(OR_LIKE_SEARCH_KEY, orLikeConditions);
        }
        return params;
    }

    private List<String> normalizeKeywordFields(List<String> keywordFields) {
        if (keywordFields == null || keywordFields.isEmpty()) {
            return List.of();
        }
        return keywordFields.stream()
                .map(this::normalizeField)
                .filter(StringUtils::isNotBlank)
                .filter(field -> !isInternalField(field))
                .distinct()
                .toList();
    }

    private List<BusinessRecordSelectorResultVO.SelectorColumnVO> resolveColumns(BusinessRecordSelectorQueryDTO query,
                                                                                 Set<String> requestedFields) {
        List<BusinessRecordSelectorResultVO.SelectorColumnVO> configured = columnsFromDisplayFields(query.getDisplayFields());
        if (!configured.isEmpty()) {
            return configured;
        }
        List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns = requestedFields.stream()
                .filter(field -> !"id".equals(field))
                .filter(field -> !isInternalField(field))
                .limit(8)
                .map(field -> column(field, field, null, null))
                .toList();
        return columns.isEmpty() ? List.of(column("id", "id", null, null)) : columns;
    }

    private List<BusinessRecordSelectorResultVO.SelectorColumnVO> columnsFromDisplayFields(List<String> displayFields) {
        if (displayFields == null || displayFields.isEmpty()) {
            return List.of();
        }
        List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns = new ArrayList<>();
        for (String item : displayFields) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String[] parts = item.split(":", 2);
            String field = normalizeField(parts[0]);
            if (StringUtils.isBlank(field) || isInternalField(field)) {
                continue;
            }
            String label = parts.length > 1 ? parts[1].trim() : field;
            columns.add(column(field, label, null, null));
        }
        return columns;
    }

    private Map<String, Object> normalizeRecord(Map<String, Object> row,
                                                List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns,
                                                Set<String> allowedFields) {
        Map<String, Object> record = new LinkedHashMap<>();
        if (row == null) {
            return record;
        }
        Object id = firstValue(row, "id", "recordId");
        if (id != null) {
            record.put("id", String.valueOf(id));
        }
        for (BusinessRecordSelectorResultVO.SelectorColumnVO column : columns) {
            String field = column.getField();
            Object value = readValue(row, field);
            record.put(field, stringifyLong(value));
        }
        record.put("_raw", stringifyLongValues(projectAllowedFields(row, allowedFields)));
        return record;
    }

    private Map<String, String> resolveFieldMappings(BusinessRecordSelectorQueryDTO query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query.getFieldMappings() == null) {
            return result;
        }
        for (BusinessRecordSelectorQueryDTO.FieldMappingDTO mapping : query.getFieldMappings()) {
            if (mapping == null || StringUtils.isBlank(mapping.getSourceField()) || StringUtils.isBlank(mapping.getTargetField())) {
                continue;
            }
            String sourceField = normalizeField(mapping.getSourceField());
            if (StringUtils.isNotBlank(sourceField) && !isInternalField(sourceField)) {
                result.put(sourceField, mapping.getTargetField().trim());
            }
        }
        return result;
    }

    private Set<String> resolveRequestedFields(BusinessRecordSelectorQueryDTO query) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("id");
        if (query.getDisplayFields() != null) {
            query.getDisplayFields().forEach(item -> {
                String field = normalizeDisplayField(item);
                if (StringUtils.isNotBlank(field) && !isInternalField(field)) {
                    fields.add(field);
                }
            });
        }
        if (query.getKeywordFields() != null) {
            query.getKeywordFields().forEach(field -> addAllowedField(fields, field));
        }
        if (query.getSearchParams() != null) {
            query.getSearchParams().keySet().stream()
                    .filter(field -> !OR_LIKE_SEARCH_KEY.equals(field))
                    .forEach(field -> addAllowedField(fields, field));
        }
        if (query.getFieldMappings() != null) {
            query.getFieldMappings().forEach(mapping -> {
                if (mapping != null) {
                    addAllowedField(fields, mapping.getSourceField());
                }
            });
        }
        return fields;
    }

    private Set<String> resolveAllowedFields(BusinessRecordSelectorQueryDTO query,
                                             List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns) {
        Set<String> fields = resolveRequestedFields(query);
        if (columns != null) {
            columns.forEach(column -> addAllowedField(fields, column.getField()));
        }
        return fields;
    }

    private void addAllowedField(Set<String> fields, String rawField) {
        String field = normalizeField(rawField);
        if (StringUtils.isNotBlank(field) && !isInternalField(field)) {
            fields.add(field);
        }
    }

    private String normalizeDisplayField(String item) {
        if (StringUtils.isBlank(item)) {
            return null;
        }
        return normalizeField(item.split(":", 2)[0]);
    }

    private String normalizeField(String field) {
        String normalized = StringUtils.trimToNull(field);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("record.")) {
            normalized = normalized.substring("record.".length());
        } else if (normalized.startsWith("row.")) {
            normalized = normalized.substring("row.".length());
        }
        return StringUtils.trimToNull(normalized);
    }

    private boolean isInternalField(String field) {
        return StringUtils.isBlank(field) || field.startsWith("_") || SYSTEM_FIELDS.contains(field);
    }

    private Map<String, Object> projectAllowedFields(Map<String, Object> row, Set<String> allowedFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (allowedFields == null || allowedFields.isEmpty()) {
            return result;
        }
        for (String field : allowedFields) {
            if (isInternalField(field)) {
                continue;
            }
            Object value = "id".equals(field) ? firstValue(row, "id", "recordId") : readValue(row, field);
            if (value != null || row.containsKey(field) || row.containsKey(camelToSnake(field))) {
                result.put(field, value);
            }
        }
        return result;
    }

    private Object readValue(Map<String, Object> row, String field) {
        if (row == null || StringUtils.isBlank(field)) {
            return null;
        }
        if (row.containsKey(field)) {
            return row.get(field);
        }
        String snake = camelToSnake(field);
        if (row.containsKey(snake)) {
            return row.get(snake);
        }
        return row.get(snakeToCamel(field));
    }

    private BusinessRecordSelectorResultVO.SelectorColumnVO column(String field, String label, String type, Integer width) {
        BusinessRecordSelectorResultVO.SelectorColumnVO column = new BusinessRecordSelectorResultVO.SelectorColumnVO();
        column.setField(field);
        column.setLabel(StringUtils.defaultIfBlank(label, field));
        column.setType(type);
        column.setWidth(width);
        return column;
    }

    private Object stringifyLong(Object value) {
        if (value instanceof Long || value instanceof java.math.BigInteger) {
            return String.valueOf(value);
        }
        return value;
    }

    private Map<String, Object> stringifyLongValues(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, stringifyLong(value)));
        return result;
    }

    private Object firstValue(Map<String, Object> source, String... fields) {
        for (String field : fields) {
            if (source.containsKey(field)) {
                return source.get(field);
            }
            String snake = camelToSnake(field);
            if (source.containsKey(snake)) {
                return source.get(snake);
            }
        }
        return null;
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof String text && StringUtils.isBlank(text);
    }

    private String camelToSnake(String value) {
        return value == null ? null : value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String snakeToCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return builder.toString();
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId == null ? 1L : tenantId;
    }
}
