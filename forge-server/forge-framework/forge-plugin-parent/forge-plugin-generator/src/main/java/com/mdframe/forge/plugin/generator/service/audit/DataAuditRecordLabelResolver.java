package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DataAuditRecordLabelResolver {

    private static final int MAX_LABEL_LENGTH = 255;

    private DataAuditRecordLabelResolver() {
    }

    static String resolve(AiBusinessObject object,
                          AiCrudConfig config,
                          Map<String, Object> before,
                          Map<String, Object> after,
                          Collection<LowcodeFieldSchema> fields,
                          String recordId,
                          ObjectMapper objectMapper) {
        Map<String, Object> record = after != null ? after : before;
        if (record == null || record.isEmpty()) {
            return recordId;
        }
        List<LowcodeFieldSchema> availableFields = fields == null ? List.of() : new ArrayList<>(fields);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, documentNoField(config, objectMapper));
        addCandidate(candidates, object == null ? null : object.getDisplayField());
        availableFields.stream().filter(DataAuditRecordLabelResolver::isBusinessNumberField)
                .map(LowcodeFieldSchema::getField).forEach(field -> addCandidate(candidates, field));
        availableFields.stream().filter(DataAuditRecordLabelResolver::isNameField)
                .map(LowcodeFieldSchema::getField).forEach(field -> addCandidate(candidates, field));

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String value = readableValue(record, candidate, availableFields);
            if (StringUtils.isNotBlank(value)) {
                values.add(value);
            }
            if (values.size() >= 2) {
                break;
            }
        }
        String label = String.join(" · ", values);
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(label, recordId), MAX_LABEL_LENGTH);
    }

    private static String documentNoField(AiCrudConfig config, ObjectMapper objectMapper) {
        if (config == null || objectMapper == null || StringUtils.isBlank(config.getOptions())) {
            return null;
        }
        try {
            JsonNode options = objectMapper.readTree(config.getOptions());
            return StringUtils.trimToNull(options.path("documentNoField").asText(null));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addCandidate(Collection<String> candidates, String candidate) {
        if (StringUtils.isNotBlank(candidate)) {
            candidates.add(candidate.trim());
        }
    }

    private static String readableValue(Map<String, Object> record,
                                        String candidate,
                                        List<LowcodeFieldSchema> fields) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addCandidate(aliases, candidate);
        LowcodeFieldSchema matchedField = null;
        for (LowcodeFieldSchema field : fields) {
            if (field == null
                    || (!sameKey(field.getField(), candidate) && !sameKey(field.getColumnName(), candidate))) {
                continue;
            }
            matchedField = field;
            addCandidate(aliases, field.getField());
            addCandidate(aliases, field.getColumnName());
        }
        if (matchedField != null && isSensitive(matchedField)) {
            return null;
        }
        for (String alias : aliases) {
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                if (!sameKey(entry.getKey(), alias)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
                    return null;
                }
                return StringUtils.trimToNull(String.valueOf(value));
            }
        }
        return null;
    }

    private static boolean isBusinessNumberField(LowcodeFieldSchema field) {
        if (field == null || Boolean.TRUE.equals(field.getSystemField())) {
            return false;
        }
        String code = normalize(field.getField());
        String label = StringUtils.defaultString(field.getLabel());
        return code.endsWith("no") || "code".equals(code) || code.endsWith("number")
                || label.contains("编号") || label.contains("单号") || label.contains("编码");
    }

    private static boolean isNameField(LowcodeFieldSchema field) {
        if (field == null || Boolean.TRUE.equals(field.getSystemField())) {
            return false;
        }
        String code = normalize(field.getField());
        String label = StringUtils.defaultString(field.getLabel());
        return "name".equals(code) || "title".equals(code)
                || code.endsWith("title") || "subject".equals(code)
                || label.contains("名称") || label.contains("标题") || label.contains("主题");
    }

    private static boolean isSensitive(LowcodeFieldSchema field) {
        String sensitiveType = StringUtils.trimToEmpty(field.getSensitiveType());
        return !sensitiveType.isEmpty() && !"NONE".equalsIgnoreCase(sensitiveType);
    }

    private static boolean sameKey(String left, String right) {
        return StringUtils.isNotBlank(left) && StringUtils.isNotBlank(right)
                && normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return StringUtils.defaultString(value).replace("_", "").toLowerCase(Locale.ROOT);
    }
}
