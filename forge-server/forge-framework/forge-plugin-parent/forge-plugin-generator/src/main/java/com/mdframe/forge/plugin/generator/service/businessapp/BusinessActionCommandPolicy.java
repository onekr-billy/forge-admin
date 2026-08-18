package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 事务型业务命令的纯协议策略。
 *
 * <p>该类不读取数据库或 Session，确保发布检查、运行时和测试使用同一组失败关闭规则。</p>
 */
final class BusinessActionCommandPolicy {

    static final String MODE_LOCAL_TRANSACTION = "LOCAL_TRANSACTION";
    static final String MODE_ORCHESTRATION = "ORCHESTRATION";

    private static final int MAX_INPUT_FIELDS = 100;
    private static final int MAX_CONTEXT_DEPTH = 8;
    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern SAFE_PATH = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9_]{0,63}(\\.[A-Za-z][A-Za-z0-9_]{0,63}){0,7}$");
    private static final Pattern QUERY_SOURCE_KEY = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9_.:/-]{0,128}$");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Set<String> LOCAL_STEP_TYPES = Set.of(
            "CREATE_RECORD", "UPDATE_FIELD", "ADJUST_NUMBER", "TRANSITION_STATUS", "ASSERT_RECORD", "FOREACH");
    private static final Set<String> INPUT_TYPES = Set.of(
            "TEXT", "NUMBER", "INTEGER", "MONEY", "BOOLEAN", "DATE", "DATETIME", "SELECT");
    private static final Set<String> RESERVED_INPUT_KEYS = Set.of(
            "tenantid", "tenant_id", "userid", "user_id", "activeorgid", "active_org_id",
            "mainorgid", "main_org_id", "createby", "create_by", "updateby", "update_by",
            "createdept", "create_dept", "delflag", "del_flag", "permission", "permissions",
            "role", "roles", "roleids", "role_ids", "__proto__", "prototype", "constructor");
    private static final Set<String> FORBIDDEN_CONFIG_KEYS = Set.of(
            "url", "apiurl", "requesturl", "header", "headers", "authorization", "credential",
            "credentials", "secret", "token", "password", "sql", "script", "handler", "spel",
            "groovy", "javascript", "endpoint");
    private static final Set<String> MAPPING_SOURCE_TYPES = Set.of(
            "record", "parent", "parentrecord", "parent_record",
            "form", "formdata", "form_data", "context", "system", "static");
    private static final Set<String> CALL_API_TARGETS = Set.of("STEP_CONTEXT", "FORM_DATA");
    private static final Set<String> CALL_API_FAILURE_STRATEGIES = Set.of("THROW", "LOG_AND_CONTINUE");
    private static final Set<String> CALL_API_RESULT_MODES = Set.of("ROOT", "FIRST_ROW");
    private static final Set<String> SYSTEM_SOURCE_FIELDS = Set.of(
            "userId", "tenantId", "activeOrgId", "activeOrgName", "mainOrgId",
            "username", "realName", "correlationId", "recordId", "parentRecordId",
            "childRecordId", "relationKey", "objectCode");

    private BusinessActionCommandPolicy() {
    }

    static void validateIdempotencyKey(String value) {
        String key = StringUtils.trimToNull(value);
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BusinessException("业务动作幂等键无效，长度需为 8～128 且仅包含安全字符");
        }
    }

    static String resolveExecutionMode(BusinessObjectActionVO action, List<BusinessActionStepDTO> steps) {
        Map<String, Object> config = action == null || action.getActionConfig() == null
                ? Map.of() : action.getActionConfig();
        String configured = normalizeMode(config.get("executionMode"));
        String mode = configured == null
                ? (allLocalSteps(steps) ? MODE_LOCAL_TRANSACTION : MODE_ORCHESTRATION)
                : configured;
        validateExecutionMode(mode, steps);
        return mode;
    }

    static String normalizeMode(Object value) {
        String mode = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (mode == null) {
            return null;
        }
        mode = mode.replace('-', '_').trim().toUpperCase(Locale.ROOT);
        if (!Set.of(MODE_LOCAL_TRANSACTION, MODE_ORCHESTRATION).contains(mode)) {
            throw new BusinessException("不支持的业务动作执行模式: " + mode);
        }
        return mode;
    }

    static void validateExecutionMode(String mode, List<BusinessActionStepDTO> steps) {
        String normalized = normalizeMode(mode);
        if (!MODE_LOCAL_TRANSACTION.equals(normalized)) {
            return;
        }
        validateLocalSteps(steps == null ? List.of() : steps);
    }

    private static void validateLocalSteps(List<BusinessActionStepDTO> steps) {
        for (BusinessActionStepDTO step : steps) {
            if (step == null) {
                continue;
            }
            String type = normalizeStepType(step.getStepType());
            if (!LOCAL_STEP_TYPES.contains(type)) {
                throw new BusinessException("本地事务动作不允许步骤类型: " + type);
            }
            if (Boolean.FALSE.equals(step.getRollbackOnFailure())) {
                throw new BusinessException("本地事务步骤必须在失败时回滚: " + type);
            }
            if ("FOREACH".equals(type)) {
                validateLocalNestedSteps(step.getStepConfig());
            }
        }
    }

    private static void validateLocalNestedSteps(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("steps");
        if (!(raw instanceof Collection<?>)) {
            raw = config == null ? null : config.get("stepList");
            if (!(raw instanceof Collection<?>)) {
                return;
            }
        }
        Collection<?> collection = (Collection<?>) raw;
        List<BusinessActionStepDTO> nested = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            BusinessActionStepDTO step = new BusinessActionStepDTO();
            step.setStepType(text(map.get("stepType")));
            step.setRollbackOnFailure(booleanValue(map.get("rollbackOnFailure"), null));
            Object stepConfig = map.get("stepConfig");
            step.setStepConfig(toStringMap(stepConfig instanceof Map<?, ?> ? stepConfig : map));
            nested.add(step);
        }
        validateLocalSteps(nested);
    }

    private static boolean allLocalSteps(List<BusinessActionStepDTO> steps) {
        try {
            validateLocalSteps(steps == null ? List.of() : steps);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    static Map<String, Object> projectFormData(BusinessObjectActionVO action, Map<String, Object> rawInput) {
        Map<String, Object> input = rawInput == null ? Map.of() : rawInput;
        assertSafeValue(input, "formData", 0, false);
        List<Map<String, Object>> schema = inputSchema(action);
        if (schema.isEmpty() && !hasDeclaredInputSchema(action)) {
            if (input.size() > MAX_INPUT_FIELDS) {
                throw new BusinessException("业务动作输入字段过多");
            }
            return new LinkedHashMap<>(input);
        }

        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Map<String, Object> definition : schema) {
            String name = StringUtils.trimToNull(text(definition.get("name")));
            validateInputName(name);
            if (definitions.putIfAbsent(name, definition) != null) {
                throw new BusinessException("业务动作输入字段重复: " + name);
            }
        }
        Set<String> unknown = new LinkedHashSet<>(input.keySet());
        unknown.removeAll(definitions.keySet());
        if (!unknown.isEmpty()) {
            throw new BusinessException("业务动作包含未声明输入字段: " + unknown.iterator().next());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : definitions.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> definition = entry.getValue();
            Object value = input.get(name);
            boolean required = Boolean.TRUE.equals(booleanValue(definition.get("required"), false));
            if (isEmpty(value)) {
                if (required) {
                    throw new BusinessException("业务动作输入不能为空: " + name);
                }
                if (input.containsKey(name)) {
                    result.put(name, null);
                }
                continue;
            }
            result.put(name, convertInput(name, value, definition));
        }
        return result;
    }

    static void validateDefinition(BusinessObjectActionVO action, List<BusinessActionStepDTO> steps) {
        Map<String, Object> config = action == null || action.getActionConfig() == null
                ? Map.of() : action.getActionConfig();
        assertSafeConfiguration(config, "actionConfig");
        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Map<String, Object> definition : inputSchema(action)) {
            String name = StringUtils.trimToNull(text(definition.get("name")));
            validateInputName(name);
            if (definitions.putIfAbsent(name, definition) != null) {
                throw new BusinessException("业务动作输入字段重复: " + name);
            }
            String type = StringUtils.defaultIfBlank(text(definition.get("type")), "TEXT")
                    .trim().toUpperCase(Locale.ROOT);
            if (!INPUT_TYPES.contains(type)) {
                throw new BusinessException("业务动作输入类型不支持: " + type);
            }
            if ("MONEY".equals(type)) {
                validateMoneyDefinition(name, definition);
            }
            BigDecimal min = decimal(definition.get("min"));
            BigDecimal max = decimal(definition.get("max"));
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw new BusinessException("业务动作输入最小值不能大于最大值: " + name);
            }
            int maxLength = intValue(definition.get("maxLength"), 2000);
            if (maxLength < 1 || maxLength > 2000) {
                throw new BusinessException("业务动作输入长度配置不正确: " + name);
            }
        }
        validateStepDefinitions(steps == null ? List.of() : steps);
        resolveExecutionMode(action, steps);
    }

    private static void validateStepDefinitions(List<BusinessActionStepDTO> steps) {
        for (BusinessActionStepDTO step : steps) {
            if (step == null) {
                continue;
            }
            String type = normalizeStepType(step.getStepType());
            Map<String, Object> config = step.getStepConfig() == null ? Map.of() : step.getStepConfig();
            assertSafeConfiguration(config, "stepConfig." + type);
            validateOptionalPath(config, "targetRecordIdField", "recordIdField");
            if ("CREATE_RECORD".equals(type) || "UPDATE_FIELD".equals(type)) {
                validateMappings(BusinessActionStepConfigHelper.firstList(
                        config, "fieldMapping", "fieldMappings", "fields", "params"));
                validateStaticFields(config, "staticValues", "values", "data");
            }
            if ("UPDATE_FIELD".equals(type) || "ADJUST_NUMBER".equals(type) || "ASSERT_RECORD".equals(type)) {
                validateMappings(BusinessActionStepConfigHelper.firstList(
                        config, "expectedFieldMappings", "expectedMappings", "conditions"));
                validateStaticFields(config, "expectedValues", "expected");
            }
            if ("ASSERT_RECORD".equals(type)) {
                validateNumericConstraints(config);
            }
            if ("TRANSITION_STATUS".equals(type)) {
                validateStatusTransition(config);
            }
            if ("ADJUST_NUMBER".equals(type)) {
                validateMappings(BusinessActionStepConfigHelper.firstList(config, "adjustments", "fields"));
            }
            if ("FOREACH".equals(type)) {
                validateRequiredPath(config, "collectionPath");
                validateStepDefinitions(toNestedSteps(config));
            }
            if ("CALL_API".equals(type)) {
                validateCallApiStep(step, config);
            }
        }
    }

    /**
     * CALL_API 与字段查询事件共用 paramMappings/resultMappings 的受控路径协议，
     * 但执行时只允许调用已登记的 EXTERNAL_API 查询源。
     */
    static void validateCallApiStep(BusinessActionStepDTO step, Map<String, Object> config) {
        String sourceType = StringUtils.upperCase(BusinessActionStepConfigHelper.firstText(
                config, "sourceType", "querySourceType"));
        if (!"EXTERNAL_API".equals(sourceType)) {
            throw new BusinessException("CALL_API 只允许调用 EXTERNAL_API 查询源");
        }
        String sourceKey = BusinessActionStepConfigHelper.firstText(config, "sourceKey", "querySourceKey");
        if (StringUtils.isBlank(sourceKey) || !QUERY_SOURCE_KEY.matcher(sourceKey).matches()) {
            throw new BusinessException("CALL_API 查询源编码为空、过长或格式非法");
        }

        validateCallApiMappings(config);
        validateCallApiResults(config);

        String resultMode = StringUtils.upperCase(StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "resultMode"), "ROOT"));
        if (!CALL_API_RESULT_MODES.contains(resultMode)) {
            throw new BusinessException("CALL_API 结果取值方式仅支持 ROOT、FIRST_ROW");
        }

        String failureStrategy = StringUtils.upperCase(StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "failureStrategy"), "THROW"));
        if (!CALL_API_FAILURE_STRATEGIES.contains(failureStrategy)) {
            throw new BusinessException("CALL_API 失败处理策略仅支持 THROW、LOG_AND_CONTINUE");
        }
        boolean rollbackOnFailure = step == null || !Boolean.FALSE.equals(step.getRollbackOnFailure());
        if ("LOG_AND_CONTINUE".equals(failureStrategy) == rollbackOnFailure) {
            throw new BusinessException("CALL_API 失败策略必须与 rollbackOnFailure 保持一致");
        }
    }

    private static void validateCallApiMappings(Map<String, Object> config) {
        Object rawMappings = BusinessActionStepConfigHelper.firstValue(config, "paramMappings", "parameterMappings");
        if (rawMappings == null) {
            return;
        }
        if (!(rawMappings instanceof Collection<?>)) {
            throw new BusinessException("CALL_API 参数映射必须是数组");
        }
        Set<String> params = new LinkedHashSet<>();
        for (Object raw : (Collection<?>) rawMappings) {
            Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(raw);
            if (mapping.isEmpty()) {
                throw new BusinessException("CALL_API 参数映射格式不正确");
            }
            String param = BusinessActionStepConfigHelper.firstText(mapping, "param", "name", "target");
            if (StringUtils.isBlank(param) || !param.matches("^[A-Za-z_][A-Za-z0-9_.-]{0,127}$")
                    || RESERVED_INPUT_KEYS.contains(normalizeKey(param)) || !params.add(param)) {
                throw new BusinessException("CALL_API 参数名为空、格式非法或重复: " + StringUtils.defaultString(param));
            }
            Map<String, Object> normalized = new LinkedHashMap<>(mapping);
            normalized.put("targetField", param);
            String source = StringUtils.upperCase(BusinessActionStepConfigHelper.firstText(mapping, "source"));
            if (source != null) {
                switch (source) {
                    case "FORM_FIELD" -> {
                        normalized.put("sourceType", "form");
                        normalized.put("sourceField", BusinessActionStepConfigHelper.firstText(mapping, "field"));
                    }
                    case "RECORD_FIELD" -> {
                        normalized.put("sourceType", "record");
                        normalized.put("sourceField", BusinessActionStepConfigHelper.firstText(mapping, "field"));
                    }
                    case "CONTEXT_PATH", "ROUTE_QUERY" -> {
                        normalized.put("sourceType", "context");
                        normalized.put("sourceField", BusinessActionStepConfigHelper.firstText(mapping, "path"));
                    }
                    case "SYSTEM_CONTEXT", "SYSTEM" -> {
                        normalized.put("sourceType", "system");
                        normalized.put("sourceField", BusinessActionStepConfigHelper.firstText(mapping, "path", "field"));
                    }
                    case "STATIC", "STATIC_VALUE" -> normalized.put("sourceType", "static");
                    default -> throw new BusinessException("CALL_API 参数来源不受支持: " + source);
                }
            }
            validateMappings(List.of(normalized));
        }
    }

    private static void validateCallApiResults(Map<String, Object> config) {
        Object rawMappings = BusinessActionStepConfigHelper.firstValue(config, "resultMappings", "responseMappings");
        if (rawMappings == null) {
            return;
        }
        if (!(rawMappings instanceof Collection<?>)) {
            throw new BusinessException("CALL_API 结果映射必须是数组");
        }
        Set<String> targets = new LinkedHashSet<>();
        for (Object raw : (Collection<?>) rawMappings) {
            Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(raw);
            if (mapping.isEmpty()) {
                throw new BusinessException("CALL_API 结果映射格式不正确");
            }
            String from = BusinessActionStepConfigHelper.firstText(mapping, "from", "source", "path");
            if (StringUtils.isNotBlank(from)) {
                validatePath(from);
            }
            String to = BusinessActionStepConfigHelper.firstText(mapping, "to", "targetField", "field");
            if (StringUtils.isBlank(to) || !SAFE_KEY.matcher(to).matches()
                    || RESERVED_INPUT_KEYS.contains(normalizeKey(to)) || !targets.add(to)) {
                throw new BusinessException("CALL_API 结果目标字段为空、格式非法或重复: " + StringUtils.defaultString(to));
            }
            String target = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "target", "targetType"), "STEP_CONTEXT"));
            if (!CALL_API_TARGETS.contains(target)) {
                throw new BusinessException("CALL_API 结果目标仅支持 STEP_CONTEXT、FORM_DATA");
            }
            String whenMissing = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "whenMissing"), "KEEP"));
            if (!Set.of("CLEAR", "KEEP").contains(whenMissing)) {
                throw new BusinessException("CALL_API 结果缺失处理方式仅支持 CLEAR、KEEP");
            }
        }
    }

    private static void validateMappings(List<?> rawMappings) {
        for (Object raw : rawMappings) {
            Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(raw);
            if (mapping.isEmpty()) {
                continue;
            }
            String targetField = BusinessActionStepConfigHelper.firstText(
                    mapping, "targetField", "target", "field", "name");
            if (StringUtils.isNotBlank(targetField)) {
                validateWritableField(targetField);
            }
            if (mapping.containsKey("value") || mapping.containsKey("staticValue")) {
                continue;
            }
            String sourceType = StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "sourceType", "type"), "record")
                    .trim().toLowerCase(Locale.ROOT);
            if (!MAPPING_SOURCE_TYPES.contains(sourceType)) {
                throw new BusinessException("业务动作字段来源不受支持: " + sourceType);
            }
            if ("static".equals(sourceType)) {
                continue;
            }
            String sourceField = BusinessActionStepConfigHelper.firstText(
                    mapping, "sourceField", "source", "formField", "field");
            if (StringUtils.isBlank(sourceField)) {
                continue;
            }
            if ("system".equals(sourceType)) {
                if (!SYSTEM_SOURCE_FIELDS.contains(sourceField)) {
                    throw new BusinessException("业务动作系统字段不受支持: " + sourceField);
                }
                continue;
            }
            validatePath(sourceField);
            if ("context".equals(sourceType) && !sourceField.startsWith("routeQuery.")) {
                throw new BusinessException("业务动作客户端上下文只允许读取 routeQuery");
            }
        }
    }

    private static void validateStaticFields(Map<String, Object> config, String... keys) {
        Map<String, Object> values = BusinessActionStepConfigHelper.asMap(
                BusinessActionStepConfigHelper.firstValue(config, keys));
        values.keySet().forEach(BusinessActionCommandPolicy::validateWritableField);
    }

    private static void validateStatusTransition(Map<String, Object> config) {
        String statusField = BusinessActionStepConfigHelper.firstText(
                config, "statusField", "status", "targetField");
        validateWritableField(statusField);
        Object fromValue = BusinessActionStepConfigHelper.firstValue(config, "fromValue", "from");
        Object toValue = BusinessActionStepConfigHelper.firstValue(config, "toValue", "to");
        validateStatusValue("fromValue", fromValue);
        validateStatusValue("toValue", toValue);
        if (StringUtils.equals(String.valueOf(fromValue), String.valueOf(toValue))) {
            throw new BusinessException("状态迁移的起止值不能相同");
        }
    }

    private static void validateNumericConstraints(Map<String, Object> config) {
        for (Object raw : BusinessActionStepConfigHelper.firstList(
                config, "numericConstraints", "fieldComparisons")) {
            Map<String, Object> constraint = BusinessActionStepConfigHelper.asMap(raw);
            String field = BusinessActionStepConfigHelper.firstText(constraint, "field", "targetField", "target");
            validateWritableField(field);
            String operator = StringUtils.upperCase(BusinessActionStepConfigHelper.firstText(
                    constraint, "operator", "op"), Locale.ROOT);
            if (!Set.of("GT", "GTE", "LT", "LTE", "EQ", "NEQ").contains(operator)) {
                throw new BusinessException("数值比较操作符不受支持: " + operator);
            }
            if (constraint.containsKey("value") || constraint.containsKey("staticValue")) {
                validateNumericValue(constraint.get(constraint.containsKey("value") ? "value" : "staticValue"));
                continue;
            }
            String sourceType = StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(constraint, "sourceType", "type"), "record")
                    .trim().toLowerCase(Locale.ROOT);
            if (!MAPPING_SOURCE_TYPES.contains(sourceType)) {
                throw new BusinessException("数值比较来源不受支持: " + sourceType);
            }
            String sourceField = BusinessActionStepConfigHelper.firstText(
                    constraint, "sourceField", "source", "formField", "field");
            if (StringUtils.isBlank(sourceField)) {
                throw new BusinessException("数值比较来源字段不能为空: " + field);
            }
            if ("system".equals(sourceType)) {
                if (!SYSTEM_SOURCE_FIELDS.contains(sourceField)) {
                    throw new BusinessException("数值比较系统字段不受支持: " + sourceField);
                }
            } else {
                validatePath(sourceField);
                if ("context".equals(sourceType) && !sourceField.startsWith("routeQuery.")) {
                    throw new BusinessException("数值比较客户端上下文只允许读取 routeQuery");
                }
                if ("static".equals(sourceType)) {
                    throw new BusinessException("静态数值比较必须配置 value");
                }
            }
        }
    }

    private static void validateNumericValue(Object value) {
        if (value == null) {
            throw new BusinessException("数值比较值不能为空");
        }
        try {
            new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException("数值比较值必须为有效数字");
        }
    }

    private static void validateStatusValue(String key, Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw new BusinessException("状态迁移" + key + "必须是单值");
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        if (text == null || text.length() > 64) {
            throw new BusinessException("状态迁移" + key + "不能为空且不能超过64个字符");
        }
    }

    private static void validateMoneyDefinition(String name, Map<String, Object> definition) {
        int scale = intValue(definition.get("scale"), 2);
        if (scale < 0 || scale > 6) {
            throw new BusinessException("金额输入小数位必须为0～6: " + name);
        }
        BigDecimal min = decimal(definition.get("min"));
        BigDecimal max = decimal(definition.get("max"));
        if (min != null && min.stripTrailingZeros().scale() > scale
                || max != null && max.stripTrailingZeros().scale() > scale) {
            throw new BusinessException("金额输入上下限超过配置小数位: " + name);
        }
    }

    private static void validateWritableField(String field) {
        String value = StringUtils.trimToNull(field);
        if (value == null || !SAFE_KEY.matcher(value).matches()
                || RESERVED_INPUT_KEYS.contains(normalizeKey(value))) {
            throw new BusinessException("业务动作目标字段受保护或格式无效: " + StringUtils.defaultString(value));
        }
    }

    private static void validateOptionalPath(Map<String, Object> config, String... keys) {
        String path = BusinessActionStepConfigHelper.firstText(config, keys);
        if (StringUtils.isNotBlank(path)) {
            validatePath(path);
        }
    }

    private static void validateRequiredPath(Map<String, Object> config, String key) {
        String path = BusinessActionStepConfigHelper.firstText(config, key);
        if (StringUtils.isBlank(path)) {
            throw new BusinessException("业务动作路径不能为空: " + key);
        }
        validatePath(path);
    }

    private static void validatePath(String path) {
        String value = StringUtils.trimToNull(path);
        if (value == null || !SAFE_PATH.matcher(value).matches()) {
            throw new BusinessException("业务动作字段路径格式无效: " + StringUtils.defaultString(value));
        }
        for (String segment : value.split("\\.")) {
            if (RESERVED_INPUT_KEYS.contains(normalizeKey(segment))) {
                throw new BusinessException("业务动作字段路径包含受保护字段: " + segment);
            }
        }
    }

    private static List<BusinessActionStepDTO> toNestedSteps(Map<String, Object> config) {
        Object raw = config.get("steps");
        if (!(raw instanceof Collection<?>)) {
            raw = config.get("stepList");
        }
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<BusinessActionStepDTO> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BusinessException("业务动作嵌套步骤格式不正确");
            }
            BusinessActionStepDTO nested = new BusinessActionStepDTO();
            nested.setStepType(text(map.get("stepType")));
            nested.setRollbackOnFailure(booleanValue(map.get("rollbackOnFailure"), null));
            nested.setStepConfig(BusinessActionStepConfigHelper.asMap(map.get("stepConfig")));
            result.add(nested);
        }
        return result;
    }

    static Map<String, Object> sanitizeClientContext(Map<String, Object> rawContext) {
        if (rawContext == null || rawContext.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object routeQuery = rawContext.get("routeQuery");
        if (!(routeQuery instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> sanitized = toStringMap(map);
        assertSafeValue(sanitized, "context.routeQuery", 0, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeQuery", sanitized);
        return result;
    }

    static void assertSafeConfiguration(Object value, String path) {
        assertSafeValue(value, StringUtils.defaultIfBlank(path, "actionConfig"), 0, true);
    }

    private static void assertSafeValue(Object value, String path, int depth, boolean rejectConfigurationKeys) {
        if (depth > MAX_CONTEXT_DEPTH) {
            throw new BusinessException("业务动作配置嵌套过深: " + path);
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = normalizeKey(key);
                if (RESERVED_INPUT_KEYS.contains(normalized)) {
                    throw new BusinessException("业务动作包含受保护字段: " + key);
                }
                if (rejectConfigurationKeys && FORBIDDEN_CONFIG_KEYS.contains(normalized)) {
                    throw new BusinessException("业务动作包含禁止配置: " + key);
                }
                assertSafeValue(entry.getValue(), path + "." + key, depth + 1, rejectConfigurationKeys);
            }
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                assertSafeValue(item, path + "[" + index++ + "]", depth + 1, rejectConfigurationKeys);
            }
        }
    }

    private static List<Map<String, Object>> inputSchema(BusinessObjectActionVO action) {
        Object raw = action == null || action.getActionConfig() == null
                ? null : action.getActionConfig().get("inputSchema");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Collection<?> collection)) {
            throw new BusinessException("业务动作输入定义必须是数组");
        }
        if (collection.size() > MAX_INPUT_FIELDS) {
            throw new BusinessException("业务动作输入字段过多");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BusinessException("业务动作输入定义格式不正确");
            }
            result.add(toStringMap(map));
        }
        return result;
    }

    private static boolean hasDeclaredInputSchema(BusinessObjectActionVO action) {
        return action != null
                && action.getActionConfig() != null
                && action.getActionConfig().get("inputSchema") instanceof Collection<?>;
    }

    private static Object convertInput(String name, Object value, Map<String, Object> definition) {
        String type = StringUtils.defaultIfBlank(text(definition.get("type")), "TEXT")
                .trim().toUpperCase(Locale.ROOT);
        if (!INPUT_TYPES.contains(type)) {
            throw new BusinessException("业务动作输入类型不支持: " + type);
        }
        try {
            return switch (type) {
                case "NUMBER" -> boundedNumber(name, new BigDecimal(String.valueOf(value)), definition, false);
                case "INTEGER" -> boundedNumber(name, new BigDecimal(String.valueOf(value)), definition, true).longValueExact();
                case "MONEY" -> moneyToMinor(name, new BigDecimal(String.valueOf(value)), definition);
                case "BOOLEAN" -> strictBoolean(name, value);
                case "DATE" -> LocalDate.parse(String.valueOf(value)).toString();
                case "DATETIME" -> LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')).toString();
                case "SELECT" -> scalarValue(name, value);
                default -> boundedText(name, value, definition);
            };
        } catch (BusinessException e) {
            throw e;
        } catch (ArithmeticException | NumberFormatException | DateTimeParseException e) {
            throw new BusinessException("业务动作输入格式不正确: " + name);
        }
    }

    private static BigDecimal boundedNumber(String name, BigDecimal value,
                                            Map<String, Object> definition, boolean integer) {
        if (integer && value.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("业务动作输入必须为整数: " + name);
        }
        BigDecimal min = decimal(definition.get("min"));
        BigDecimal max = decimal(definition.get("max"));
        if (min != null && value.compareTo(min) < 0) {
            throw new BusinessException("业务动作输入小于最小值: " + name);
        }
        if (max != null && value.compareTo(max) > 0) {
            throw new BusinessException("业务动作输入大于最大值: " + name);
        }
        return value;
    }

    private static Long moneyToMinor(String name, BigDecimal value, Map<String, Object> definition) {
        int scale = intValue(definition.get("scale"), 2);
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > scale) {
            throw new BusinessException("金额输入超过允许的小数位，禁止静默舍入: " + name);
        }
        BigDecimal min = decimal(definition.get("min"));
        BigDecimal max = decimal(definition.get("max"));
        if (min == null) {
            min = BigDecimal.ZERO;
        }
        if (normalized.compareTo(min) < 0) {
            throw new BusinessException("金额输入小于最小值: " + name);
        }
        if (max != null && normalized.compareTo(max) > 0) {
            throw new BusinessException("金额输入大于最大值: " + name);
        }
        try {
            return normalized.movePointRight(scale).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException("金额输入超出最小货币单位范围: " + name);
        }
    }

    private static Object scalarValue(String name, Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw new BusinessException("业务动作输入必须为单值: " + name);
        }
        return value;
    }

    private static String boundedText(String name, Object value, Map<String, Object> definition) {
        String text = String.valueOf(value);
        int maxLength = intValue(definition.get("maxLength"), 2000);
        if (maxLength < 1 || maxLength > 2000) {
            throw new BusinessException("业务动作输入长度配置不正确: " + name);
        }
        if (text.length() > maxLength) {
            throw new BusinessException("业务动作输入超过最大长度: " + name);
        }
        return text;
    }

    private static Boolean strictBoolean(String name, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        throw new BusinessException("业务动作输入必须为布尔值: " + name);
    }

    private static void validateInputName(String name) {
        if (name == null || !SAFE_KEY.matcher(name).matches()) {
            throw new BusinessException("业务动作输入字段名无效: " + StringUtils.defaultString(name));
        }
        if (RESERVED_INPUT_KEYS.contains(normalizeKey(name))) {
            throw new BusinessException("业务动作输入字段受保护: " + name);
        }
    }

    private static String normalizeStepType(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace('-', '_')
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeKey(String value) {
        return StringUtils.defaultString(value)
                .replace("-", "")
                .replace("_", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static BigDecimal decimal(Object value) {
        return value == null || StringUtils.isBlank(String.valueOf(value))
                ? null : new BigDecimal(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Boolean booleanValue(Object value, Boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static boolean isEmpty(Object value) {
        return value == null || value instanceof String text && StringUtils.isBlank(text);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }
}
