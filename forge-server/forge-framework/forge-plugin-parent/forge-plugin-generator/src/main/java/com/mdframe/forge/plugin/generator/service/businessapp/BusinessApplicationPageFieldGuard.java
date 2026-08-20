package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
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
public final class BusinessApplicationPageFieldGuard {

    private BusinessApplicationPageFieldGuard() {
    }

    public static void assertCompatible(
            boolean hasBusinessData,
            List<LowcodeFieldSchema> existingFields,
            List<BusinessFieldDTO> requestedFields) {
        if (!hasBusinessData) {
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
                throw new BusinessException("字段“" + label + "”已有数据，不能删除");
            }
            if (!sameStorageType(existing, requested)) {
                throw new BusinessException("字段“" + label + "”已有数据，不能修改字段类型");
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
            List<LowcodeFieldSchema> existingFields) {
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
        for (ComponentBinding before : persisted.values()) {
            ComponentBinding after = requested.get(before.id());
            if (after == null) {
                throw new BusinessException("字段“" + before.label() + "”已有数据，不能删除");
            }
            if (!Objects.equals(before.fieldCode(), after.fieldCode())) {
                throw new BusinessException("字段“" + before.label() + "”已有数据，不能修改字段编码");
            }
            if (!Objects.equals(before.componentKey(), after.componentKey())) {
                throw new BusinessException("字段“" + before.label() + "”已有数据，不能修改字段类型");
            }
        }
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
