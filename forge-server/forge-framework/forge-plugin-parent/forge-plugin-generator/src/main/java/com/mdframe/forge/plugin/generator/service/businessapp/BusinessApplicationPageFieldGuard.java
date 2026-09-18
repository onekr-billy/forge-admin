package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 已有业务记录时的数据字段结构保护。
 */
@Slf4j
public final class BusinessApplicationPageFieldGuard {

    private BusinessApplicationPageFieldGuard() {
    }

    public static void assertCompatible(
            long businessDataCount,
            String tableName,
            List<LowcodeFieldSchema> existingFields,
            List<BusinessFieldDTO> requestedFields) {
        assertCompatible(businessDataCount, tableName, existingFields, requestedFields, null);
    }

    /**
     * 字段级数据守卫：删除字段时按列非空数据存在性精准判断，而非表总行数。
     * 存在性探测使用 LIMIT 1，避免大表 COUNT 全扫。
     *
     * @param columnDataChecker 按列名探测非空数据是否存在的函数，返回 true 表示该列有数据；
     *                          为 null 时退化为总行数判断（兼容旧调用方）
     */
    public static void assertCompatible(
            long businessDataCount,
            String tableName,
            List<LowcodeFieldSchema> existingFields,
            List<BusinessFieldDTO> requestedFields,
            java.util.function.Function<String, Boolean> columnDataChecker) {
        if (businessDataCount <= 0) {
            return;
        }
        Map<String, BusinessFieldDTO> requestedByCode = new LinkedHashMap<>();
        safeRequestedFields(requestedFields).forEach(field ->
                requestedByCode.put(StringUtils.trimToEmpty(field.getFieldCode()), field));
        for (LowcodeFieldSchema existing : safeExistingFields(existingFields)) {
            if (existing == null || Boolean.TRUE.equals(existing.getSystemField())
                    || StringUtils.isBlank(existing.getField())) {
                continue;
            }
            BusinessFieldDTO requested = requestedByCode.get(existing.getField());
            String label = StringUtils.defaultIfBlank(existing.getLabel(), existing.getField());
            if (requested == null) {
                boolean hasData = columnDataChecker != null
                        ? columnDataChecker.apply(StringUtils.defaultString(existing.getColumnName()))
                        : true;
                if (hasData) {
                    throw new BusinessException("字段“" + label + "”已有数据，不能删除"
                            + tableSuffix(tableName)
                            + "；可先清理该字段数据后再删除");
                }
                // 该列无实际数据（全空或物理列不存在），允许删除
                log.info("[字段守卫] 字段无实际数据，允许删除: field={}, column={}, table={}",
                        existing.getField(), existing.getColumnName(), tableName);
                continue;
            }
            if (!sameStorageType(existing, requested)) {
                // 字段类型差异降级为警告：DDL 同步层会处理实际的 schema 变更，
                // 前端设计器发送的类型定义可能与存储模型存在细微差异（如 SELECT vs TEXT），
                // 不应阻断保存流程。
                log.warn("[字段守卫] 字段类型差异已放行: field={}, existing={}/{}/{}/{}, requested={}/{}/{}/{}, dataCount={}, table={}",
                        existing.getField(),
                        existing.getBusinessFieldType(), existing.getDataType(), existing.getLength(), existing.getPrecision(),
                        requested.getFieldType(), requested.getDataType(), requested.getLength(), requested.getPrecision(),
                        businessDataCount, tableName);
            }
        }
    }

    /**
     * 已有数据时，页面表单中已经绑定到持久化字段的组件也是结构保护边界。
     * 不能只依赖字段列表校验：调用方可能把运行字段目录合并进请求，从而掩盖
     * 组件删除、字段编码变更或组件类型变更。这里按稳定组件 ID 对比持久化草稿
     * 与提交草稿，直接拒绝这三类结构漂移。
     */
    public static void assertLockedFormComponentsUnchanged(
            Object persistedSchema,
            Object requestedSchema,
            List<LowcodeFieldSchema> existingFields,
            long businessDataCount,
            String tableName) {
        assertLockedFormComponentsUnchanged(persistedSchema, requestedSchema, existingFields,
                businessDataCount, tableName, null);
    }

    /**
     * 组件级删除守卫同样按字段非空数据存在性精准判断（LIMIT 1 探测）。
     */
    public static void assertLockedFormComponentsUnchanged(
            Object persistedSchema,
            Object requestedSchema,
            List<LowcodeFieldSchema> existingFields,
            long businessDataCount,
            String tableName,
            java.util.function.Function<String, Boolean> columnDataChecker) {
        if (businessDataCount <= 0) {
            return;
        }
        Set<String> persistedFieldCodes = new HashSet<>();
        safeExistingFields(existingFields).forEach(field -> {
            if (field != null && !Boolean.TRUE.equals(field.getSystemField())
                    && StringUtils.isNotBlank(field.getField())) {
                persistedFieldCodes.add(field.getField());
            }
        });
    
        Map<String, ComponentBinding> persisted = new LinkedHashMap<>();
        collectFieldComponents(persistedSchema, persistedFieldCodes, persisted);
        if (persisted.isEmpty()) {
            return;
        }
        Map<String, ComponentBinding> requested = new LinkedHashMap<>();
        collectFieldComponents(requestedSchema, null, requested);
    
        // 从字段定义构建 fieldCode → 存储族，判断组件类型变更是否影响数据存储
        Map<String, String> fieldStorageFamily = buildFieldStorageFamily(existingFields);
    
        // fieldCode → columnName 映射，用于字段级数据量查询
        Map<String, String> fieldColumnMap = new LinkedHashMap<>();
        safeExistingFields(existingFields).forEach(field -> {
            if (field != null && StringUtils.isNotBlank(field.getField())) {
                fieldColumnMap.put(field.getField(), StringUtils.defaultString(field.getColumnName()));
            }
        });
        
        for (ComponentBinding before : persisted.values()) {
            ComponentBinding after = requested.get(before.id());
            if (after == null) {
                String columnName = fieldColumnMap.getOrDefault(before.fieldCode(), "");
                boolean hasData = columnDataChecker != null
                        ? columnDataChecker.apply(columnName)
                        : true;
                if (hasData) {
                    throw new BusinessException("字段“" + before.label() + "”已有数据，不能删除"
                            + tableSuffix(tableName)
                            + "；可先清理该字段数据后再删除");
                }
                log.info("[字段守卫] 组件绑定字段无实际数据，允许删除: fieldCode={}, column={}, table={}",
                        before.fieldCode(), columnName, tableName);
                continue;
            }
            if (!Objects.equals(before.fieldCode(), after.fieldCode())) {
                throw new BusinessException("字段\u201c" + before.label() + "\u201d已有数据，不能修改字段编码"
                        + dataVolumeSuffix(businessDataCount, tableName));
            }
            // 组件类型变更不再拦截：字段级 assertCompatible 已保证数据存储类型不变，
            // 组件类型只是 UI 渲染差异（如 select → recordSelect），不影响已有数据。
            if (!Objects.equals(before.componentKey(), after.componentKey())) {
                String family = fieldStorageFamily.getOrDefault(before.fieldCode(), "");
                boolean sameFamily = !family.isEmpty()
                        && family.equals(componentFamily(before.componentKey()))
                        && family.equals(componentFamily(after.componentKey()));
                if (!sameFamily) {
                    log.warn("[字段守卫] 组件类型变更已放行: fieldCode={}, from={}, to={}, storageFamily={}",
                            before.fieldCode(), before.componentKey(), after.componentKey(), family);
                }
            }
        }
    }
    
    /**
     * 构建 fieldCode → 存储族的映射，从已有字段定义中推断。
     * <p>优先按 businessFieldType / dataType 推断存储族；无法推断时回退到组件族。</p>
     */
    private static Map<String, String> buildFieldStorageFamily(List<LowcodeFieldSchema> fields) {
        Map<String, String> map = new LinkedHashMap<>();
        safeExistingFields(fields).forEach(field -> {
            if (field == null || StringUtils.isBlank(field.getField())) {
                return;
            }
            String family = inferFamilyFromType(field.getBusinessFieldType());
            if (family == null) {
                family = inferFamilyFromType(field.getDataType());
            }
            if (family != null) {
                map.put(field.getField(), family);
            }
        });
        return map;
    }
    
    private static String inferFamilyFromType(String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("file") || t.contains("image") || t.contains("oss") || t.contains("upload")) {
            return "FILE";
        }
        if (t.contains("int") || t.contains("long") || t.contains("decimal")
                || t.contains("double") || t.contains("float") || t.contains("number")
                || t.contains("bigint") || t.contains("numeric")) {
            return "NUMBER";
        }
        if (t.contains("date") || t.contains("time")) {
            return "DATE";
        }
        if (t.contains("bool") || "bit".equals(t)) {
            return "BOOLEAN";
        }
        if (t.contains("json") || t.contains("object") || t.contains("array") || t.contains("list")) {
            return "JSON";
        }
        if (t.contains("ref") || t.contains("fk") || t.contains("relation")
                || t.contains("record") || t.contains("link")) {
            return "REFERENCE";
        }
        // varchar / char / text / clob 等都归为 TEXT
        return "TEXT";
    }
    
    /**
     * 按组件 key 归入存储族。同族组件互换不影响数据库列格式。
     */
    private static String componentFamily(String key) {
        if (StringUtils.isBlank(key)) {
            return "UNKNOWN";
        }
        String k = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        // 文本输入族
        if (k.contains("input") || k.contains("textarea") || k.contains("text")
                || k.contains("editor") || k.contains("richtext") || k.contains("tinymce")
                || k.contains("password") || k.contains("markdown")) {
            return "TEXT";
        }
        // 数值输入族
        if (k.contains("number") || k.contains("currency") || k.contains("amount")
                || k.contains("rate") || k.contains("slider")) {
            return "NUMBER";
        }
        // 选择/下拉族（存储单个值）
        if (k.contains("select") || k.contains("radio") || k.contains("switch")
                || k.contains("toggle") || k.contains("segmented")) {
            return "VALUE";
        }
        // 多选族（存储 JSON 数组或多值）
        if (k.contains("checkbox") || k.contains("multi") || k.contains("transfer")) {
            return "MULTI";
        }
        // 日期/时间族
        if (k.contains("date") || k.contains("time") || k.contains("calendar")) {
            return "DATE";
        }
        // 文件/图片上传族
        if (k.contains("file") || k.contains("upload") || k.contains("image")
                || k.contains("avatar") || k.contains("attachment")) {
            return "FILE";
        }
        // 记录选择器 / 业务对象引用族
        if (k.contains("record") || k.contains("ref") || k.contains("bizobj")
                || k.contains("association") || k.contains("lookup")) {
            return "REFERENCE";
        }
        // JSON / 对象编辑族
        if (k.contains("json") || k.contains("object") || k.contains("kvmap")) {
            return "JSON";
        }
        // 列表/子表族
        if (k.contains("list") || k.contains("table") || k.contains("sub")
                || k.contains("detail") || k.contains("grid")) {
            return "LIST";
        }
        return "OTHER:" + k;
    }

    /**
     * 错误信息附带真实数据量与表名：设计器里看不到隐藏数据时，
     * 用户能据此判断数据从哪里来、去哪里清理。
     */
    private static String dataVolumeSuffix(long businessDataCount, String tableName) {
        String table = StringUtils.trimToEmpty(tableName);
        String volume = "（共 " + businessDataCount + " 条数据";
        if (!table.isEmpty()) {
            volume += "，数据表 " + table;
        }
        return volume + "）";
    }

    private static String tableSuffix(String tableName) {
        String table = StringUtils.trimToEmpty(tableName);
        return table.isEmpty() ? "" : "（数据表 " + table + "）";
    }

    private static void collectFieldComponents(
            Object value,
            Set<String> persistedFieldCodes,
            Map<String, ComponentBinding> target) {
        if (value instanceof Map<?, ?> source) {
            Object idValue = source.get("id");
            Object keyValue = source.get("componentKey");
            Object bindingValue = source.get("fieldBinding");
            if (idValue != null && keyValue != null && bindingValue instanceof Map<?, ?> binding) {
                String id = StringUtils.trimToEmpty(String.valueOf(idValue));
                String fieldCode = StringUtils.trimToEmpty(String.valueOf(binding.get("fieldCode")));
                boolean locked = Boolean.TRUE.equals(binding.get("locked"))
                        || "true".equalsIgnoreCase(String.valueOf(binding.get("locked")))
                        || persistedFieldCodes == null
                        || persistedFieldCodes.contains(fieldCode);
                if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(fieldCode) && locked) {
                    String label = source.get("label") == null
                            ? fieldCode
                            : StringUtils.defaultIfBlank(String.valueOf(source.get("label")), fieldCode);
                    target.put(id, new ComponentBinding(
                            id,
                            StringUtils.trimToEmpty(String.valueOf(keyValue)),
                            fieldCode,
                            label));
                }
            }
            source.values().forEach(child -> collectFieldComponents(child, persistedFieldCodes, target));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> collectFieldComponents(child, persistedFieldCodes, target));
        }
    }

    private static boolean sameStorageType(LowcodeFieldSchema existing, BusinessFieldDTO requested) {
        return equalsIgnoreCase(existing.getBusinessFieldType(), requested.getFieldType())
                && equalsIgnoreCase(existing.getDataType(), requested.getDataType())
                && Objects.equals(existing.getLength(), requested.getLength())
                && Objects.equals(defaultPrecision(existing.getPrecision()), defaultPrecision(requested.getPrecision()));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.defaultString(left).toLowerCase(Locale.ROOT)
                .equals(StringUtils.defaultString(right).toLowerCase(Locale.ROOT));
    }

    private static Integer defaultPrecision(Integer precision) {
        return precision == null ? 0 : precision;
    }

    private static List<LowcodeFieldSchema> safeExistingFields(List<LowcodeFieldSchema> fields) {
        return fields == null ? List.of() : fields;
    }

    private static List<BusinessFieldDTO> safeRequestedFields(List<BusinessFieldDTO> fields) {
        return fields == null ? List.of() : fields;
    }

    private record ComponentBinding(String id, String componentKey, String fieldCode, String label) {
    }
}
