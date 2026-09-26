package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.formula.FormulaRuntimeContext;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.service.crypto.LowcodeEncryptConfigParser;
import com.mdframe.forge.plugin.generator.service.formula.VirtualFormulaRuntime;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeComponentCatalog;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategy;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeStrategyFactory;
import com.mdframe.forge.starter.crypto.desensitize.strategy.DesensitizeType;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.trans.spi.DictValueProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 动态字段值处理流水线。
 *
 * <p>写入链负责金额、结构化值和加密规范化；读取链按稳定顺序执行字段别名、解密、
 * 金额/结构化投影、虚拟公式、翻译和脱敏。打印链使用严格失败策略，避免部分明文输出。</p>
 */
@Slf4j
final class DynamicCrudFieldValuePipeline {

    private final ObjectMapper objectMapper;
    private final DictValueProvider dictValueProvider;
    private final DesensitizeStrategyFactory desensitizeStrategyFactory;
    private final PersistentCryptoService persistentCryptoService;
    private final LowcodeEncryptConfigParser encryptConfigParser;
    private final VirtualFormulaRuntime virtualFormulaRuntime;

    DynamicCrudFieldValuePipeline(ObjectMapper objectMapper,
                                  DictValueProvider dictValueProvider,
                                  DesensitizeStrategyFactory desensitizeStrategyFactory,
                                  PersistentCryptoService persistentCryptoService,
                                  LowcodeEncryptConfigParser encryptConfigParser,
                                  VirtualFormulaRuntime virtualFormulaRuntime) {
        this.objectMapper = objectMapper;
        this.dictValueProvider = dictValueProvider;
        this.desensitizeStrategyFactory = desensitizeStrategyFactory;
        this.persistentCryptoService = persistentCryptoService;
        this.encryptConfigParser = encryptConfigParser;
        this.virtualFormulaRuntime = virtualFormulaRuntime;
    }

    void applyRead(List<Map<String, Object>> rows, AiCrudConfig config) {
        applyRuntimeFieldAliases(rows, config);
        applyDecrypt(rows, config.getEncryptConfig());
        applyMoneyDisplayProjection(rows, config);
        applyStructuredFieldDisplayProjection(rows, config);
        applyVirtualFormulas(config, rows, false);
        applyDictTranslation(rows, buildEffectiveTransConfig(config), false);
        applyDesensitize(rows, config.getDesensitizeConfig(), false);
    }

    void applyPrintRead(List<Map<String, Object>> rows, AiCrudConfig config) {
        applyRuntimeFieldAliases(rows, config);
        applyDecrypt(rows, config.getEncryptConfig());
        applyMoneyDisplayProjection(rows, config);
        applyStructuredFieldDisplayProjection(rows, config);
        applyVirtualFormulas(config, rows, true);
        applyDictTranslation(rows, buildEffectiveTransConfig(config), true);
        applyDesensitize(rows, config.getDesensitizeConfig(), true);
    }

    void applyMoneyStorageWrite(Map<String, Object> data, AiCrudConfig config) {
        if (data == null || data.isEmpty() || config == null) {
            return;
        }
        for (MoneyFieldContract field : resolveMinorMoneyFields(config)) {
            String key = resolveMoneyDataKey(data, field);
            if (key != null && data.get(key) != null) {
                data.put(key, toMinorMoney(field, data.get(key)));
            }
        }
    }

    void applyStructuredFieldStorageWrite(Map<String, Object> data, AiCrudConfig config) {
        if (data == null || data.isEmpty() || config == null) {
            return;
        }
        for (StructuredFieldContract field : resolveStructuredFields(config)) {
            String key = resolveStructuredDataKey(data, field);
            Object value = key == null ? null : data.get(key);
            if (value == null || value instanceof String) {
                continue;
            }
            try {
                data.put(key, objectMapper.writeValueAsString(value));
            } catch (Exception e) {
                throw new BusinessException("字段值序列化失败: " + field.fieldName());
            }
        }
    }

    void applyEncrypt(Map<String, Object> data, String encryptConfigJson) {
        if (StringUtils.isBlank(encryptConfigJson) || data == null || data.isEmpty()) {
            return;
        }
        try {
            for (LowcodeEncryptConfigParser.FieldRule rule : encryptConfigParser.parse(encryptConfigJson)) {
                String dataKey = resolveWriteEncryptKey(data, rule);
                if (dataKey == null || data.get(dataKey) == null) {
                    continue;
                }
                Object value = data.get(dataKey);
                if (value instanceof String plainText && StringUtils.isNotBlank(plainText)) {
                    data.put(dataKey, persistentCryptoService.encrypt(plainText, rule.algorithm()));
                    log.debug("[DynamicCrudFieldValuePipeline] 加密字段: {}, algorithm: {}",
                            rule.fieldName(), rule.algorithm());
                }
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 加密处理失败, exceptionType={}",
                    e.getClass().getSimpleName());
            throw new BusinessException("低代码加密字段处理失败，请检查持久化密钥配置", e);
        }
    }

    void removeMaskedDesensitizedWriteColumns(Map<String, Object> data,
                                               AiCrudConfig config,
                                               Map<String, String> columnMapping) {
        if (data == null || data.isEmpty() || config == null || StringUtils.isBlank(config.getDesensitizeConfig())) {
            return;
        }
        Set<String> sensitiveColumns = resolveDesensitizedColumns(config, columnMapping);
        if (!sensitiveColumns.isEmpty()) {
            data.entrySet().removeIf(entry -> sensitiveColumns.contains(entry.getKey())
                    && entry.getValue() instanceof String text && text.contains("*"));
        }
    }

    void applyStructuredFieldDisplayProjection(List<Map<String, Object>> rows, AiCrudConfig config) {
        if (rows == null || rows.isEmpty() || config == null) {
            return;
        }
        List<StructuredFieldContract> fields = resolveStructuredFields(config);
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            for (StructuredFieldContract field : fields) {
                String key = resolveStructuredDataKey(row, field);
                Object value = key == null ? null : row.get(key);
                if (value != null && !(value instanceof Collection<?>)) {
                    row.put(key, parseStructuredValue(value));
                }
            }
        }
    }

    private Object parseStructuredValue(Object rawValue) {
        if (rawValue instanceof String value) {
            String text = value.trim();
            if (text.isEmpty()) {
                return new ArrayList<>();
            }
            try {
                JsonNode node = objectMapper.readTree(text);
                if (node != null && node.isArray()) {
                    return objectMapper.convertValue(node, new TypeReference<List<Object>>() { });
                }
            } catch (Exception ignored) {
                // 兼容早期以逗号分隔保存的历史值。
            }
            if (text.contains(",")) {
                return Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .toList();
            }
            return List.of(value);
        }
        if (rawValue instanceof JsonNode node && node.isArray()) {
            return objectMapper.convertValue(node, new TypeReference<List<Object>>() { });
        }
        return rawValue;
    }

    private String resolveStructuredDataKey(Map<String, Object> data, StructuredFieldContract field) {
        String camelColumn = DynamicQueryGenerator.snakeToCamel(field.columnName());
        for (String candidate : List.of(field.fieldName(), field.columnName(), camelColumn)) {
            if (StringUtils.isNotBlank(candidate) && data.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<StructuredFieldContract> resolveStructuredFields(AiCrudConfig config) {
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return List.of();
        }
        List<StructuredFieldContract> result = new ArrayList<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null) {
                continue;
            }
            String componentType = StringUtils.trimToNull(field.getComponentType());
            String businessType = StringUtils.upperCase(StringUtils.trimToEmpty(field.getBusinessFieldType()));
            if (componentType == null && "CHECKBOX".equals(businessType)) {
                componentType = "checkbox";
            } else if (componentType == null && "MULTI_SELECT".equals(businessType)) {
                componentType = "transfer";
            }
            if (!LowcodeComponentCatalog.isStructuredValueComponent(componentType)
                    || StringUtils.isBlank(field.getField())) {
                continue;
            }
            String columnName = StringUtils.defaultIfBlank(
                    field.getColumnName(), DynamicQueryGenerator.camelToSnake(field.getField()));
            result.add(new StructuredFieldContract(field.getField(), columnName, componentType));
        }
        return result;
    }

    void applyMoneyDisplayProjection(List<Map<String, Object>> rows, AiCrudConfig config) {
        if (rows == null || rows.isEmpty() || config == null) {
            return;
        }
        List<MoneyFieldContract> fields = resolveMinorMoneyFields(config);
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            for (MoneyFieldContract field : fields) {
                String key = resolveMoneyDataKey(row, field);
                if (key != null && row.get(key) != null) {
                    row.put(key, fromMinorMoney(field, row.get(key)));
                }
            }
        }
    }

    private String resolveMoneyDataKey(Map<String, Object> data, MoneyFieldContract field) {
        String camelColumn = DynamicQueryGenerator.snakeToCamel(field.columnName());
        for (String candidate : List.of(field.fieldName(), field.columnName(), camelColumn)) {
            if (StringUtils.isNotBlank(candidate) && data.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Long toMinorMoney(MoneyFieldContract field, Object rawValue) {
        try {
            String text = String.valueOf(rawValue).trim();
            if (StringUtils.isBlank(text)) {
                return null;
            }
            BigDecimal amount = new BigDecimal(text).stripTrailingZeros();
            if (amount.scale() > field.scale()) {
                throw new BusinessException("金额输入超过允许的小数位，禁止静默舍入: " + field.fieldName());
            }
            if (field.minValue() != null && amount.compareTo(field.minValue()) < 0) {
                throw new BusinessException("金额输入小于最小值: " + field.fieldName());
            }
            if (field.maxValue() != null && amount.compareTo(field.maxValue()) > 0) {
                throw new BusinessException("金额输入大于最大值: " + field.fieldName());
            }
            return amount.movePointRight(field.scale()).longValueExact();
        } catch (BusinessException e) {
            throw e;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BusinessException("金额输入格式不正确: " + field.fieldName());
        }
    }

    private BigDecimal fromMinorMoney(MoneyFieldContract field, Object rawValue) {
        try {
            return new BigDecimal(String.valueOf(rawValue)).movePointLeft(field.scale());
        } catch (NumberFormatException e) {
            throw new BusinessException("金额存储值格式不正确: " + field.fieldName());
        }
    }

    private List<MoneyFieldContract> resolveMinorMoneyFields(AiCrudConfig config) {
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return List.of();
        }
        List<MoneyFieldContract> result = new ArrayList<>();
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null || !isMinorMoneyField(field)) {
                continue;
            }
            String fieldName = StringUtils.trimToNull(field.getField());
            if (fieldName == null) {
                continue;
            }
            String columnName = StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(field.getColumnName()), DynamicQueryGenerator.camelToSnake(fieldName));
            if (columnName == null) {
                continue;
            }
            int scale = field.getPrecision() == null ? 2 : field.getPrecision();
            if (scale < 0 || scale > 6) {
                throw new BusinessException("MONEY 字段小数位配置不正确: " + fieldName);
            }
            Map<String, Object> basicProps = field.getBasicProps() == null ? Map.of() : field.getBasicProps();
            result.add(new MoneyFieldContract(fieldName, columnName, scale,
                    toBigDecimal(basicProps.get("min")), toBigDecimal(basicProps.get("max"))));
        }
        return result;
    }

    private boolean isMinorMoneyField(LowcodeFieldSchema field) {
        String businessType = StringUtils.upperCase(StringUtils.trimToEmpty(field.getBusinessFieldType()));
        String componentType = StringUtils.lowerCase(StringUtils.trimToEmpty(field.getComponentType()));
        String dataType = StringUtils.lowerCase(StringUtils.trimToEmpty(field.getDataType()));
        boolean money = "MONEY".equals(businessType) || "money".equals(componentType);
        return money && Set.of("tinyint", "smallint", "mediumint", "int", "integer", "bigint", "long")
                .contains(dataType);
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

    private void applyRuntimeFieldAliases(List<Map<String, Object>> rows, AiCrudConfig config) {
        if (rows == null || rows.isEmpty() || config == null) {
            return;
        }
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null || modelSchema.getFields() == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                String fieldName = field == null ? null : StringUtils.trimToNull(field.getField());
                String columnName = field == null ? null : StringUtils.trimToNull(field.getColumnName());
                if (fieldName == null || columnName == null || row.containsKey(fieldName)) {
                    continue;
                }
                if (row.containsKey(columnName)) {
                    row.put(fieldName, row.get(columnName));
                    continue;
                }
                String camelColumnName = DynamicQueryGenerator.snakeToCamel(columnName);
                if (row.containsKey(camelColumnName)) {
                    row.put(fieldName, row.get(camelColumnName));
                }
            }
            applyReferenceDisplayAliases(row, modelSchema);
        }
    }

    private void applyReferenceDisplayAliases(Map<String, Object> row, LowcodeModelSchema modelSchema) {
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null || !field.isSelectionLabelField()) {
                continue;
            }
            String displayFieldName = field.referenceDisplayFieldName();
            String displayColumnName = field.referenceDisplayColumnName();
            if (displayFieldName == null || displayColumnName == null || row.containsKey(displayFieldName)) {
                continue;
            }
            if (row.containsKey(displayColumnName)) {
                row.put(displayFieldName, row.get(displayColumnName));
                continue;
            }
            String camelDisplayColumn = DynamicQueryGenerator.snakeToCamel(displayColumnName);
            if (row.containsKey(camelDisplayColumn)) {
                row.put(displayFieldName, row.get(camelDisplayColumn));
            }
        }
    }

    void applyDecrypt(List<Map<String, Object>> rows, String encryptConfigJson) {
        if (StringUtils.isBlank(encryptConfigJson) || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            List<LowcodeEncryptConfigParser.FieldRule> rules = encryptConfigParser.parse(encryptConfigJson);
            for (Map<String, Object> row : rows) {
                for (LowcodeEncryptConfigParser.FieldRule rule : rules) {
                    String dataKey = resolveReadEncryptKey(row, rule);
                    if (dataKey == null || row.get(dataKey) == null) {
                        continue;
                    }
                    Object value = row.get(dataKey);
                    if (value instanceof String cipherText && StringUtils.isNotBlank(cipherText)) {
                        row.put(dataKey, persistentCryptoService.decrypt(cipherText, rule.algorithm()));
                        log.debug("[DynamicCrudFieldValuePipeline] 解密字段: {}, algorithm: {}",
                                rule.fieldName(), rule.algorithm());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 解密处理失败, exceptionType={}",
                    e.getClass().getSimpleName());
            throw new BusinessException("低代码加密字段读取失败，请检查持久化密钥配置", e);
        }
    }

    private String resolveWriteEncryptKey(Map<String, Object> data, LowcodeEncryptConfigParser.FieldRule rule) {
        if (data.containsKey(rule.columnName())) {
            return rule.columnName();
        }
        if (data.containsKey(rule.fieldName())) {
            return rule.fieldName();
        }
        String snakeFieldName = DynamicQueryGenerator.camelToSnake(rule.fieldName());
        return data.containsKey(snakeFieldName) ? snakeFieldName : null;
    }

    private String resolveReadEncryptKey(Map<String, Object> row, LowcodeEncryptConfigParser.FieldRule rule) {
        if (row.containsKey(rule.fieldName())) {
            return rule.fieldName();
        }
        if (row.containsKey(rule.columnName())) {
            return rule.columnName();
        }
        String camelColumnName = DynamicQueryGenerator.snakeToCamel(rule.columnName());
        return row.containsKey(camelColumnName) ? camelColumnName : null;
    }

    private void applyDesensitize(List<Map<String, Object>> rows, String desensitizeConfigJson, boolean strict) {
        if (StringUtils.isBlank(desensitizeConfigJson) || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            JsonNode configNode = objectMapper.readTree(desensitizeConfigJson);
            if (!configNode.isObject()) {
                if (strict) {
                    throw new BusinessException("打印脱敏配置无效");
                }
                return;
            }
            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, JsonNode> entry : configNode.properties()) {
                    String fieldName = entry.getKey();
                    JsonNode ruleNode = entry.getValue();
                    if (!row.containsKey(fieldName) || row.get(fieldName) == null) {
                        continue;
                    }
                    String typeName = ruleNode.has("type") ? ruleNode.get("type").asText("CUSTOM") : "CUSTOM";
                    DesensitizeStrategy strategy = desensitizeStrategyFactory.getStrategy(
                            DesensitizeType.valueOf(typeName));
                    if (strategy == null && strict) {
                        throw new BusinessException("打印脱敏策略不可用");
                    }
                    if (strategy != null) {
                        row.put(fieldName, strategy.desensitize(String.valueOf(row.get(fieldName))));
                    }
                }
            }
        } catch (Exception e) {
            if (strict) {
                throw new BusinessException("打印脱敏处理失败，已终止输出");
            }
            log.warn("[DynamicCrudFieldValuePipeline] 脱敏处理失败", e);
        }
    }

    private Set<String> resolveDesensitizedColumns(AiCrudConfig config, Map<String, String> columnMapping) {
        Set<String> columns = new HashSet<>();
        try {
            JsonNode configNode = objectMapper.readTree(config.getDesensitizeConfig());
            if (!configNode.isObject()) {
                return columns;
            }
            for (String fieldName : iterableFieldNames(configNode)) {
                columns.add(columnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName)));
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 解析脱敏写入字段失败", e);
        }
        return columns;
    }

    private List<String> iterableFieldNames(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private String buildEffectiveTransConfig(AiCrudConfig config) {
        if (config == null) {
            return null;
        }
        Map<String, Object> rules = new LinkedHashMap<>();
        mergeTransConfig(rules, config.getTransConfig());
        mergeTransRulesFromSchema(rules, config.getColumnsSchema(), true);
        mergeTransRulesFromSchema(rules, config.getSearchSchema(), false);
        mergeTransRulesFromSchema(rules, config.getEditSchema(), false);
        if (rules.isEmpty()) {
            return config.getTransConfig();
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 合并翻译配置失败", e);
            return config.getTransConfig();
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeTransConfig(Map<String, Object> rules, String transConfigJson) {
        if (StringUtils.isBlank(transConfigJson)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(transConfigJson);
            if (node.isObject()) {
                rules.putAll(objectMapper.convertValue(node, Map.class));
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 解析翻译配置失败", e);
        }
    }

    private void mergeTransRulesFromSchema(Map<String, Object> rules, String schemaJson, boolean overrideExisting) {
        if (StringUtils.isBlank(schemaJson)) {
            return;
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            if (!schemaNode.isArray()) {
                return;
            }
            for (JsonNode item : schemaNode) {
                mergeTransRuleFromField(rules, item, overrideExisting);
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudFieldValuePipeline] 从Schema推导翻译配置失败", e);
        }
    }

    private void mergeTransRuleFromField(Map<String, Object> rules, JsonNode item, boolean overrideExisting) {
        String fieldName = firstText(item, "field", "prop", "key", "dataIndex");
        if (StringUtils.isBlank(fieldName) || (!overrideExisting && rules.containsKey(fieldName))) {
            return;
        }
        String dictType = firstText(item, "dictType");
        JsonNode renderNode = item.get("render");
        if (StringUtils.isBlank(dictType) && renderNode != null && renderNode.isObject()) {
            dictType = firstText(renderNode, "dictType");
        }
        if (StringUtils.isNotBlank(dictType)) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("dictType", dictType);
            rule.put("targetField", fieldName + "Name");
            rules.put(fieldName, rule);
            return;
        }
        String renderType = renderNode != null && renderNode.isObject() ? firstText(renderNode, "type") : "";
        String componentType = StringUtils.defaultIfBlank(firstText(item, "type", "componentType"), renderType);
        String transType = resolveTransType(componentType);
        if (StringUtils.isBlank(transType)) {
            return;
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", transType);
        rule.put("targetField", renderNode != null && renderNode.isObject()
                ? StringUtils.defaultIfBlank(firstText(renderNode, "targetField"), fieldName + "Name")
                : fieldName + "Name");
        rules.put(fieldName, rule);
    }

    private String resolveTransType(String componentType) {
        return switch (StringUtils.defaultString(componentType)) {
            case "orgTreeSelect", "orgName" -> "orgName";
            case "userSelect", "userName" -> "userName";
            case "regionTreeSelect", "regionName" -> "regionName";
            case "fileUpload", "imageUpload" -> componentType;
            default -> "";
        };
    }

    private void applyDictTranslation(List<Map<String, Object>> rows, String transConfigJson, boolean strict) {
        if (strict && StringUtils.isNotBlank(transConfigJson) && dictValueProvider == null) {
            throw new BusinessException("打印翻译服务不可用");
        }
        if (StringUtils.isBlank(transConfigJson) || rows == null || rows.isEmpty() || dictValueProvider == null) {
            return;
        }
        try {
            JsonNode configNode = objectMapper.readTree(transConfigJson);
            if (!configNode.isObject()) {
                if (strict) {
                    throw new BusinessException("打印翻译配置无效");
                }
                return;
            }
            Map<String, List<String>> orgIds = new LinkedHashMap<>();
            Map<String, List<String>> userIds = new LinkedHashMap<>();
            Map<String, List<String>> fileIds = new LinkedHashMap<>();
            Map<String, List<String>> regionCodes = new LinkedHashMap<>();
            Map<String, String> targets = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                collectTranslations(configNode, row, orgIds, userIds, fileIds, regionCodes, targets);
            }
            applyBatchTranslation(rows, orgIds, "orgName", dictValueProvider::batchGetOrgNames, targets);
            applyBatchTranslation(rows, userIds, "userName", dictValueProvider::batchGetUserNames, targets);
            applyBatchTranslation(rows, regionCodes, "regionName", dictValueProvider::batchGetRegionNames, targets);
            applyBatchTranslation(rows, fileIds, "fileUpload", dictValueProvider::batchGetFileNames, targets);
        } catch (Exception e) {
            if (strict) {
                throw new BusinessException("打印字段翻译失败，已终止输出");
            }
            log.warn("[DynamicCrudFieldValuePipeline] 翻译处理失败", e);
        }
    }

    private void collectTranslations(JsonNode configNode,
                                     Map<String, Object> row,
                                     Map<String, List<String>> orgIds,
                                     Map<String, List<String>> userIds,
                                     Map<String, List<String>> fileIds,
                                     Map<String, List<String>> regionCodes,
                                     Map<String, String> targets) {
        for (Map.Entry<String, JsonNode> entry : configNode.properties()) {
            String sourceField = entry.getKey();
            if (!row.containsKey(sourceField) || row.get(sourceField) == null) {
                continue;
            }
            JsonNode rule = entry.getValue();
            String transType = rule.has("type") ? rule.get("type").asText("") : "";
            String dictType = rule.has("dictType") ? rule.get("dictType").asText("") : "";
            String targetField = rule.has("targetField") ? rule.get("targetField").asText() : sourceField + "Name";
            String value = String.valueOf(row.get(sourceField));
            if (StringUtils.isNotBlank(dictType)) {
                String label = dictValueProvider.getLabel(dictType, value);
                if (label != null) {
                    row.put(targetField, label);
                }
                continue;
            }
            Map<String, List<String>> bucket = switch (transType) {
                case "orgName" -> orgIds;
                case "userName" -> userIds;
                case "regionName" -> regionCodes;
                case "fileUpload", "imageUpload" -> fileIds;
                default -> null;
            };
            if (bucket != null) {
                bucket.computeIfAbsent(sourceField, key -> new ArrayList<>()).add(value);
                targets.put(sourceField, targetField);
            }
        }
    }

    private void applyBatchTranslation(List<Map<String, Object>> rows,
                                       Map<String, List<String>> fieldBuckets,
                                       String transType,
                                       Function<List<String>, Map<String, String>> batchLoader,
                                       Map<String, String> targetFieldMap) {
        for (Map.Entry<String, List<String>> bucket : fieldBuckets.entrySet()) {
            String sourceField = bucket.getKey();
            List<String> ids = bucket.getValue().stream().distinct().toList();
            if (ids.isEmpty()) {
                continue;
            }
            Map<String, String> nameMap;
            try {
                nameMap = batchLoader.apply(ids);
            } catch (Exception e) {
                log.warn("[DynamicCrudFieldValuePipeline] 批量翻译失败, type={}, field={}",
                        transType, sourceField, e);
                continue;
            }
            if (nameMap == null || nameMap.isEmpty()) {
                continue;
            }
            String targetField = targetFieldMap.getOrDefault(sourceField, sourceField + "Name");
            for (Map<String, Object> row : rows) {
                Object value = row.get(sourceField);
                if (value != null) {
                    String name = nameMap.get(String.valueOf(value));
                    if (name != null) {
                        row.put(targetField, name);
                    }
                }
            }
        }
    }

    private void applyVirtualFormulas(AiCrudConfig config,
                                      List<Map<String, Object>> records,
                                      boolean printMode) {
        if (records == null || records.isEmpty()) {
            return;
        }
        LowcodeModelSchema modelSchema = parseModelSchema(config);
        if (modelSchema == null) {
            return;
        }
        for (Map<String, Object> record : records) {
            FormulaRuntimeContext context = buildFormulaRuntimeContext(config, record);
            if (printMode) {
                virtualFormulaRuntime.calculateForPrint(List.of(record), modelSchema, context);
            } else {
                virtualFormulaRuntime.calculate(List.of(record), modelSchema, context);
            }
        }
    }

    private LowcodeModelSchema parseModelSchema(AiCrudConfig config) {
        if (config == null || StringUtils.isBlank(config.getModelSchema())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception e) {
            log.warn("Failed to parse modelSchema for {}: {}", config.getConfigKey(), e.getMessage());
            return null;
        }
    }

    private FormulaRuntimeContext buildFormulaRuntimeContext(AiCrudConfig config, Map<String, Object> currentRow) {
        return new FormulaRuntimeContext(
                config.getTenantId(), extractSuiteCode(config), config.getObjectCode(), currentRow);
    }

    private String extractSuiteCode(AiCrudConfig config) {
        String configKey = config.getConfigKey();
        if (StringUtils.isBlank(configKey)) {
            return "default";
        }
        int separator = configKey.indexOf('_');
        return separator > 0 ? configKey.substring(0, separator) : configKey;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private record StructuredFieldContract(String fieldName, String columnName, String componentType) {
    }

    private record MoneyFieldContract(String fieldName,
                                      String columnName,
                                      int scale,
                                      BigDecimal minValue,
                                      BigDecimal maxValue) {
    }
}
