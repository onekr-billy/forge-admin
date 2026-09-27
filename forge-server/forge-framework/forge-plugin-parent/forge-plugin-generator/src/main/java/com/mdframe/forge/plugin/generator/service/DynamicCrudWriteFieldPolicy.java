package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.formula.FormulaRuntimeContext;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.service.formula.StoredFormulaRuntime;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeFieldValueValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 动态写入字段许可、真实列映射、事务条件与存储公式准备策略。 */
@Slf4j
final class DynamicCrudWriteFieldPolicy {

    private static final Set<String> IMMUTABLE_WRITE_FIELDS = Set.of(
            "id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
            "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time",
            "delFlag", "del_flag"
    );

    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;
    private final DynamicCrudFieldValuePipeline fieldValuePipeline;
    private final StoredFormulaRuntime storedFormulaRuntime;

    DynamicCrudWriteFieldPolicy(DynamicCrudRepository repository,
                                ObjectMapper objectMapper,
                                DynamicCrudFieldValuePipeline fieldValuePipeline,
                                StoredFormulaRuntime storedFormulaRuntime) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.fieldValuePipeline = fieldValuePipeline;
        this.storedFormulaRuntime = storedFormulaRuntime;
    }

    Set<String> buildAllowedWriteFields(AiCrudConfig config, String tableName) {
        Set<String> fields = new LinkedHashSet<>(DynamicQueryGenerator.extractFieldNames(config.getEditSchema(), objectMapper));
        addWritableModelFields(fields, config, tableName);
        addSelectionLabelFieldsFromEditSchema(fields, config, tableName);
        addStoredFormulaWriteFields(fields, config);
        fields.removeAll(IMMUTABLE_WRITE_FIELDS);
        return fields;
    }

    /**
     * 从 editSchema 的 labelValueField / fieldMappings 目标字段放行写入。
     * 动态下拉冗余名称、选中回填目标可能不在 model 的 isSelectionLabelField 判定里，
     * 但运行态会随主列一起提交，必须进白名单否则 UI 有值却入库被滤掉。
     */
    void addSelectionLabelFieldsFromEditSchema(Set<String> fields, AiCrudConfig config, String tableName) {
        if (fields == null || config == null || StringUtils.isBlank(config.getEditSchema())) {
            return;
        }
        Set<String> tableColumns = repository.getTableColumns(tableName);
        try {
            JsonNode node = objectMapper.readTree(config.getEditSchema());
            if (!node.isArray()) {
                return;
            }
            for (JsonNode item : node) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                JsonNode propsNode = item.get("props");
                if (propsNode == null || !propsNode.isObject()) {
                    continue;
                }
                addWritableAliasIfColumnExists(fields, tableColumns, text(propsNode.get("labelValueField")));
                addWritableAliasIfColumnExists(fields, tableColumns, text(propsNode.get("targetField")));
                JsonNode mappings = propsNode.get("fieldMappings");
                if (mappings == null) {
                    mappings = propsNode.get("mappings");
                }
                if (mappings != null && mappings.isArray()) {
                    for (JsonNode mapping : mappings) {
                        if (mapping == null || !mapping.isObject()) {
                            continue;
                        }
                        String target = firstNonBlank(text(mapping.get("targetField")), text(mapping.get("target")));
                        addWritableAliasIfColumnExists(fields, tableColumns, target);
                    }
                }
                JsonNode optionSource = propsNode.get("optionSource");
                if (optionSource != null && optionSource.isObject()) {
                    JsonNode optionMappings = optionSource.get("fieldMappings");
                    if (optionMappings == null) {
                        optionMappings = optionSource.get("mappings");
                    }
                    if (optionMappings != null && optionMappings.isArray()) {
                        for (JsonNode mapping : optionMappings) {
                            if (mapping == null || !mapping.isObject()) {
                                continue;
                            }
                            String target = firstNonBlank(text(mapping.get("targetField")), text(mapping.get("target")));
                            addWritableAliasIfColumnExists(fields, tableColumns, target);
                        }
                    }
                }
                String fieldName = firstNonBlank(text(item.get("field")), text(item.get("prop")), text(item.get("key")));
                if (StringUtils.isNotBlank(fieldName) && hasDynamicOptionSourceNode(optionSource)) {
                    addWritableAliasIfColumnExists(fields, tableColumns, fieldName + "Name");
                }
            }
        } catch (Exception ex) {
            log.warn("[DynamicCrud] 解析 editSchema 伴随/映射写字段失败: {}", ex.getMessage());
        }
    }

    void addWritableAliasIfColumnExists(Set<String> fields, Set<String> tableColumns, String fieldName) {
        if (StringUtils.isBlank(fieldName) || fields == null) {
            return;
        }
        String column = DynamicQueryGenerator.camelToSnake(fieldName);
        if (tableColumns != null && !tableColumns.isEmpty()
                && !tableColumns.contains(column)
                && !tableColumns.contains(fieldName)) {
            return;
        }
        addFieldAlias(fields, fieldName);
        addFieldAlias(fields, column);
    }

    boolean hasDynamicOptionSourceNode(JsonNode optionSource) {
        if (optionSource == null || !optionSource.isObject()) {
            return false;
        }
        String type = text(optionSource.get("type"));
        if (StringUtils.isBlank(type)) {
            return false;
        }
        return !"STATIC".equalsIgnoreCase(type.replace('-', '_'));
    }

    String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    void addWritableModelFields(Set<String> fields, AiCrudConfig config, String tableName) {
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null || modelSchema.getFields().isEmpty()) {
            return;
        }
        Set<String> tableColumns = repository.getTableColumns(tableName);
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (!isWritableModelField(field, tableColumns)) {
                continue;
            }
            addFieldAlias(fields, field.getField());
            addFieldAlias(fields, field.getColumnName());
            // 引用字段选中时同步提交显示名称，伴随列存在时放行写入，列表/详情回显零关联查询。
            if (field.isSelectionLabelField() && tableColumns.contains(field.referenceDisplayColumnName())) {
                addFieldAlias(fields, field.referenceDisplayFieldName());
                addFieldAlias(fields, field.referenceDisplayColumnName());
            }
        }
    }

    boolean isWritableModelField(LowcodeFieldSchema field, Set<String> tableColumns) {
        if (field == null || StringUtils.isBlank(field.getField())) {
            return false;
        }
        String fieldStatus = StringUtils.defaultString(field.getFieldStatus());
        if ("DISABLED".equalsIgnoreCase(fieldStatus) || "HIDDEN".equalsIgnoreCase(fieldStatus)) {
            return false;
        }
        if (Boolean.TRUE.equals(field.getSystemField())
                || Boolean.TRUE.equals(field.getPrimaryKey())
                || Boolean.TRUE.equals(field.getAutoIncrement())
                || Boolean.TRUE.equals(field.getReadonly())
                || Boolean.FALSE.equals(field.getFormVisible())) {
            return false;
        }
        String columnName = StringUtils.defaultIfBlank(field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField()));
        return StringUtils.isNotBlank(columnName) && tableColumns.contains(columnName);
    }

    Map<String, Object> filterInternalWriteData(AiCrudConfig config, String tableName, Map<String, Object> data) {
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        Set<String> tableColumns = repository.getTableColumns(tableName);
        Set<String> allowedFields = collectInternalWriteFields(config, tableName);
        Map<String, Object> filteredData = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (isImmutableWriteField(key)) {
                continue;
            }
            if (!allowedFields.contains(key)) {
                throw new BusinessException("字段不在模型中: " + key);
            }
            String columnName = columnMapping.getOrDefault(key, DynamicQueryGenerator.camelToSnake(key));
            repository.validateIdentifier(columnName);
            if (!tableColumns.contains(columnName)) {
                throw missingRuntimeColumn(config, tableName, key, columnName);
            }
            if (isImmutableWriteField(columnName)) {
                continue;
            }
            filteredData.put(columnName, entry.getValue());
        }
        return filteredData;
    }

    Map<String, Object> filterCommandWriteData(
            AiCrudConfig config,
            String tableName,
            Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> columnMapping = buildRuntimeColumnMapping(config, tableName);
        Set<String> tableColumns = repository.getTableColumns(tableName);
        Set<String> allowedFields = collectCommandFields(config);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            String key = StringUtils.trimToNull(entry.getKey());
            if (key == null || isImmutableWriteField(key)) {
                throw new BusinessException("事务命令字段不可写: " + StringUtils.defaultString(key));
            }
            if (!allowedFields.contains(key)
                    && !allowedFields.contains(DynamicQueryGenerator.snakeToCamel(key))
                    && !allowedFields.contains(DynamicQueryGenerator.camelToSnake(key))) {
                throw new BusinessException("事务命令字段不在已发布模型中: " + key);
            }
            String column = columnMapping.getOrDefault(key, DynamicQueryGenerator.camelToSnake(key));
            repository.validateIdentifier(column);
            if (!tableColumns.contains(column) || isImmutableWriteField(column)) {
                throw new BusinessException("事务命令字段不在已发布模型中: " + key);
            }
            if (filtered.putIfAbsent(column, entry.getValue()) != null) {
                throw new BusinessException("事务命令字段重复映射: " + key);
            }
        }
        return filtered;
    }

    Set<String> collectCommandFields(AiCrudConfig config) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getEditSchema(), objectMapper));
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper));
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field == null
                        || Boolean.TRUE.equals(field.getSystemField())
                        || Boolean.TRUE.equals(field.getPrimaryKey())
                        || "DISABLED".equalsIgnoreCase(field.getFieldStatus())) {
                    continue;
                }
                addFieldAlias(fields, field.getField());
                addFieldAlias(fields, field.getColumnName());
            }
        }
        fields.removeAll(IMMUTABLE_WRITE_FIELDS);
        return fields;
    }

    Map<String, BigDecimal> mapCommandDecimalFields(
            AiCrudConfig config,
            String tableName,
            Map<String, BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> mapped = filterCommandWriteData(config, tableName, values);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        mapped.forEach((field, value) -> {
            if (!(value instanceof BigDecimal decimal)) {
                throw new BusinessException("数值调整量格式不正确");
            }
            result.put(field, decimal);
        });
        return result;
    }

    DynamicCrudRepository.SqlCondition buildCommandExpectedCondition(
            AiCrudConfig config,
            String tableName,
            Map<String, Object> expectedFields) {
        if (expectedFields == null || expectedFields.isEmpty()) {
            return null;
        }
        Map<String, Object> mapped = filterCommandWriteData(config, tableName, expectedFields);
        fieldValuePipeline.applyEncrypt(mapped, config.getEncryptConfig());
        List<String> expressions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, Object> entry : mapped.entrySet()) {
            String param = "commandExpected" + index++;
            if (entry.getValue() == null) {
                expressions.add(entry.getKey() + " IS NULL");
            } else {
                expressions.add(entry.getKey() + " = :" + param);
                params.put(param, entry.getValue());
            }
        }
        return expressions.isEmpty() ? null
                : new DynamicCrudRepository.SqlCondition(String.join(" AND ", expressions), params);
    }

    DynamicCrudRepository.SqlCondition buildCommandNumericCondition(
            AiCrudConfig config,
            String tableName,
            List<Map<String, Object>> numericConstraints) {
        if (numericConstraints == null || numericConstraints.isEmpty()) {
            return null;
        }
        List<String> expressions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int index = 0;
        for (Map<String, Object> constraint : numericConstraints) {
            if (constraint == null || constraint.isEmpty()) {
                continue;
            }
            String field = StringUtils.trimToNull(String.valueOf(constraint.get("field")));
            String operator = StringUtils.trimToNull(String.valueOf(constraint.get("operator")));
            Object value = constraint.get("value");
            if (field == null || operator == null || value == null) {
                throw new BusinessException("数值比较配置不完整");
            }
            Map<String, Object> mapped = filterCommandWriteData(
                    config, tableName, Map.of(field, value));
            String column = mapped.keySet().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("数值比较字段无效: " + field));
            BigDecimal number = toBigDecimal(value);
            if (number == null) {
                throw new BusinessException("数值比较值必须为有效数字: " + field);
            }
            String normalizedOperator = operator.toLowerCase(Locale.ROOT);
            String sqlOperator = switch (normalizedOperator) {
                case "gt" -> ">";
                case "gte" -> ">=";
                case "lt" -> "<";
                case "lte" -> "<=";
                case "eq" -> "=";
                case "neq" -> "<>";
                default -> throw new BusinessException("不支持的数值比较操作符: " + operator);
            };
            String parameter = "commandNumeric" + index++;
            expressions.add(column + " " + sqlOperator + " :" + parameter);
            params.put(parameter, number);
        }
        return expressions.isEmpty() ? null
                : new DynamicCrudRepository.SqlCondition(String.join(" AND ", expressions), params);
    }

    DynamicCrudRepository.SqlCondition combineConditions(
            DynamicCrudRepository.SqlCondition first,
            DynamicCrudRepository.SqlCondition second) {
        if (first == null || StringUtils.isBlank(first.sql())) {
            return second;
        }
        if (second == null || StringUtils.isBlank(second.sql())) {
            return first;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (first.params() != null) {
            params.putAll(first.params());
        }
        if (second.params() != null) {
            for (Map.Entry<String, Object> entry : second.params().entrySet()) {
                if (params.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                    throw new BusinessException("事务命令条件参数冲突");
                }
            }
        }
        return new DynamicCrudRepository.SqlCondition(
                "(" + first.sql() + ") AND (" + second.sql() + ")", params);
    }

    /**
     * 构建运行态字段到真实数据库列的映射。
     *
     * <p>低代码字段编码是稳定的业务 API 契约，数据库列名则可能由设计器自动生成，
     * 两者不能假定相同。例如业务字段 {@code dpe} 可以持久化到 {@code field_input4}。
     * 数据库元数据只能提供 camelCase/snake_case 映射，因此这里必须叠加已发布模型中的
     * {@link LowcodeFieldSchema#getColumnName()} 显式映射。</p>
     */
    Map<String, String> buildRuntimeColumnMapping(AiCrudConfig config, String tableName) {
        Map<String, String> result = new LinkedHashMap<>(repository.getColumnMapping(tableName));
        if (config == null || !StringUtils.equals(tableName, config.getTableName())) {
            return result;
        }
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return result;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null) {
                continue;
            }
            String columnName = StringUtils.trimToNull(StringUtils.defaultIfBlank(
                    field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField())));
            if (columnName == null) {
                continue;
            }
            String normalizedColumn = columnName.toLowerCase(Locale.ROOT);
            putRuntimeColumnAlias(result, field.getField(), normalizedColumn);
            putRuntimeColumnAlias(result, field.getColumnName(), normalizedColumn);
        }
        return result;
    }

    void putRuntimeColumnAlias(Map<String, String> mapping, String alias, String columnName) {
        if (StringUtils.isBlank(alias) || StringUtils.isBlank(columnName)) {
            return;
        }
        mapping.put(alias, columnName);
        mapping.put(DynamicQueryGenerator.snakeToCamel(alias), columnName);
        mapping.put(DynamicQueryGenerator.camelToSnake(alias), columnName);
    }

    BusinessException missingRuntimeColumn(
            AiCrudConfig config,
            String tableName,
            String fieldName,
            String columnName) {
        String configKey = config == null ? null : config.getConfigKey();
        return new BusinessException("业务字段 " + fieldName + " 映射的数据库列 " + columnName
                + " 不存在（运行配置: " + StringUtils.defaultIfBlank(configKey, "未知")
                + "，数据表: " + StringUtils.defaultIfBlank(tableName, "未知")
                + "），请先同步低代码数据表结构后重新发布能力");
    }

    Set<String> collectInternalWriteFields(AiCrudConfig config, String tableName) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getEditSchema(), objectMapper));
        fields.addAll(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper));
        LowcodeModelSchema modelSchema = StringUtils.isNotBlank(config.getModelSchema()) ? parseModelSchema(config) : null;
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field == null) {
                    continue;
                }
                addFieldAlias(fields, field.getField());
                addFieldAlias(fields, field.getColumnName());
            }
        }
        for (String column : repository.getTableColumns(tableName)) {
            addFieldAlias(fields, column);
        }
        fields.removeAll(IMMUTABLE_WRITE_FIELDS);
        return fields;
    }

    void addFieldAlias(Set<String> fields, String field) {
        if (StringUtils.isBlank(field)) {
            return;
        }
        fields.add(field);
        fields.add(DynamicQueryGenerator.snakeToCamel(field));
        fields.add(DynamicQueryGenerator.camelToSnake(field));
    }


    Map<String, Object> applyStoredFormulasForUpdate(AiCrudConfig config,
                                                             String tableName,
                                                             Object id,
                                                             Map<String, Object> data,
                                                             DynamicCrudRepository.SqlCondition dataScopeCondition) {
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        Map<String, Object> existingRecord = repository.selectById(
                tableName, primaryKeyColumn(primaryKey), id, dataScopeCondition);
        return applyStoredFormulasForUpdate(config, tableName, id, data, dataScopeCondition, existingRecord);
    }

    Map<String, Object> applyStoredFormulasForUpdate(AiCrudConfig config,
                                                             String tableName,
                                                             Object id,
                                                             Map<String, Object> data,
                                                             DynamicCrudRepository.SqlCondition dataScopeCondition,
                                                             Map<String, Object> existingRecord) {
        if (existingRecord == null) {
            throw new BusinessException("无权限更新该数据或数据不存在");
        }
        Map<String, Object> formulaContext = DynamicQueryGenerator.convertMapToCamelCase(existingRecord);
        formulaContext.put("id", id);
        LowcodePrimaryKeyStrategy primaryKey = currentPrimaryKey();
        formulaContext.put(primaryKeyField(primaryKey), id);
        formulaContext.put(primaryKeyColumn(primaryKey), id);
        mergeWriteDataForFormula(formulaContext, data);
        applyStoredFormulas(config, formulaContext);
        copyStoredFormulaValues(config, formulaContext, data);
        return existingRecord;
    }

    void mergeWriteDataForFormula(Map<String, Object> formulaContext, Map<String, Object> data) {
        if (formulaContext == null || data == null || data.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isBlank(key)) {
                continue;
            }
            formulaContext.put(key, entry.getValue());
            formulaContext.put(DynamicQueryGenerator.snakeToCamel(key), entry.getValue());
            formulaContext.put(DynamicQueryGenerator.camelToSnake(key), entry.getValue());
        }
    }

    void addStoredFormulaWriteFields(Set<String> allowedFields, AiCrudConfig config) {
        if (allowedFields == null) {
            return;
        }
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (isStoredFormulaField(field)) {
                addFieldAlias(allowedFields, field.getField());
                addFieldAlias(allowedFields, field.getColumnName());
            }
        }
    }

    void copyStoredFormulaValues(AiCrudConfig config,
                                         Map<String, Object> source,
                                         Map<String, Object> target) {
        if (source == null || target == null) {
            return;
        }
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return;
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (!isStoredFormulaField(field) || StringUtils.isBlank(field.getField())) {
                continue;
            }
            if (source.containsKey(field.getField())) {
                target.put(field.getField(), source.get(field.getField()));
            }
        }
    }

    boolean isStoredFormulaField(LowcodeFieldSchema field) {
        if (field == null || field.getFormulaConfig() == null || field.getFormulaConfig().isEmpty()) {
            return false;
        }
        Object mode = field.getFormulaConfig().get("mode");
        return mode == null || "STORED".equalsIgnoreCase(String.valueOf(mode));
    }

    void applyStoredFormulas(AiCrudConfig config, Map<String, Object> data) {
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null) return;
        FormulaRuntimeContext ctx = buildFormulaRuntimeContext(config, data);
        storedFormulaRuntime.calculate(List.of(data), modelSchema, ctx);
    }

    LowcodeModelSchema parseModelSchema(AiCrudConfig config) {
        String json = config.getModelSchema();
        if (org.apache.commons.lang3.StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LowcodeModelSchema.class);
        } catch (Exception e) {
            log.warn("Failed to parse modelSchema for {}: {}", config.getConfigKey(), e.getMessage());
            return null;
        }
    }

    void validateFieldValues(AiCrudConfig config, Map<String, Object> data) {
        LowcodeFieldValueValidator.validate(parseModelSchema(config), data, objectMapper);
    }

    FormulaRuntimeContext buildFormulaRuntimeContext(AiCrudConfig config,
                                                        Map<String, Object> currentRow) {
        Long tenantId = config.getTenantId();
        String suiteCode = extractSuiteCode(config);
        String objectCode = config.getObjectCode();
        return new FormulaRuntimeContext(tenantId, suiteCode, objectCode, currentRow);
    }

    String extractSuiteCode(AiCrudConfig config) {
        String configKey = config.getConfigKey();
        if (org.apache.commons.lang3.StringUtils.isBlank(configKey)) {
            return "default";
        }
        int idx = configKey.indexOf("_");
        return idx > 0 ? configKey.substring(0, idx) : configKey;
    }

    private boolean isImmutableWriteField(String key) {
        if (StringUtils.isBlank(key)) {
            return true;
        }
        return IMMUTABLE_WRITE_FIELDS.contains(key)
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.snakeToCamel(key))
                || IMMUTABLE_WRITE_FIELDS.contains(DynamicQueryGenerator.camelToSnake(key));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LowcodePrimaryKeyStrategy currentPrimaryKey() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        LowcodePrimaryKeyStrategy primaryKey = context == null ? null : context.getPrimaryKey();
        if (primaryKey == null) {
            primaryKey = new LowcodePrimaryKeyStrategy();
            primaryKey.setField("id");
            primaryKey.setColumnName("id");
            primaryKey.setDataType("bigint");
            primaryKey.setAutoIncrement(true);
        }
        return primaryKey;
    }

    private String primaryKeyField(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getField(), "id");
    }

    private String primaryKeyColumn(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getColumnName(), "id");
    }
}
