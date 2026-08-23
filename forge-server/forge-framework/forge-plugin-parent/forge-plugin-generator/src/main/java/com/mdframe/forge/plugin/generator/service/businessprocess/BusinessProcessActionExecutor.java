package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 执行应用业务流程中的受控记录动作。 */
@Component
@RequiredArgsConstructor
public class BusinessProcessActionExecutor {

    private final AiCrudConfigMapper crudConfigMapper;
    private final DynamicCrudService dynamicCrudService;

    public String execute(AiBusinessProcessRun run,
                          BusinessProcessSchema schema,
                          BusinessProcessNode node) {
        Map<String, Object> config = node.getConfig() == null ? Map.of() : node.getConfig();
        String actionType = upper(text(config.get("actionType")));
        String configuredObjectCode = text(config.get("objectCode"));
        // Older action-node drafts used the generic placeholder
        // `business_object`. It is not a runtime object and must never be
        // looked up as a published CRUD config. Prefer the immutable process
        // subject snapshot when that placeholder is present.
        String objectCode = firstConcreteObjectCode(
                configuredObjectCode,
                schema.getSubject() == null ? null : schema.getSubject().getObjectCode(),
                run.getSubjectObjectCode());
        AiCrudConfig runtimeConfig = crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(
                run.getTenantId(), objectCode);
        if (runtimeConfig == null || StringUtils.isBlank(runtimeConfig.getConfigKey())) {
            throw new BusinessException("动作目标对象没有可用的发布态运行配置: " + objectCode);
        }
        Map<String, Object> data = buildFieldData(config);
        if (data.isEmpty()) {
            throw new BusinessException("动作节点没有配置有效的字段赋值");
        }
        if ("UPDATE_RECORD".equals(actionType)) {
            Object recordId = resolveUpdateRecordId(run, config, objectCode);
            dynamicCrudService.updateFieldsInternal(runtimeConfig.getConfigKey(), recordId, data);
            return "已更新 " + data.size() + " 个字段";
        }
        if ("CREATE_RECORD".equals(actionType)) {
            dynamicCrudService.insertInternal(runtimeConfig.getConfigKey(), new LinkedHashMap<>(data));
            return "已创建记录并写入 " + data.size() + " 个字段";
        }
        throw new BusinessException("动作类型尚未接入运行时: " + actionType);
    }

    private String firstConcreteObjectCode(String... candidates) {
        for (String candidate : candidates) {
            String value = StringUtils.trimToNull(candidate);
            if (value != null && !"business_object".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(candidates.length == 0 ? null : candidates[0]),
                "business_object");
    }

    private Object resolveUpdateRecordId(AiBusinessProcessRun run,
                                         Map<String, Object> config,
                                         String objectCode) {
        Object configured = firstValue(config, "targetRecordId", "recordId");
        if (configured != null && StringUtils.isNotBlank(String.valueOf(configured))) {
            return configured;
        }
        if (StringUtils.isNotBlank(run.getSubjectObjectCode())
                && !"business_object".equalsIgnoreCase(run.getSubjectObjectCode())
                && !StringUtils.equals(objectCode, run.getSubjectObjectCode())) {
            throw new BusinessException("更新其他业务对象时必须配置目标记录ID");
        }
        if (StringUtils.isBlank(run.getSubjectRecordId())) {
            throw new BusinessException("更新当前记录时缺少业务记录ID");
        }
        return run.getSubjectRecordId();
    }

    private Map<String, Object> buildFieldData(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object mappings = firstValue(config, "fieldMappings", "fieldMapping", "fields", "params");
        if (mappings instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (!(item instanceof Map<?, ?> mapping)) {
                    continue;
                }
                String field = firstText(mapping, "field", "targetField", "target", "name");
                if (StringUtils.isBlank(field)) {
                    continue;
                }
                String valueSource = upper(firstText(mapping, "valueSource", "sourceType", "type"));
                if (StringUtils.isNotBlank(valueSource)
                        && !List.of("CONSTANT", "STATIC", "VALUE").contains(valueSource)) {
                    throw new BusinessException("当前记录动作只支持固定值字段赋值: " + field);
                }
                result.put(field, mapping.containsKey("value")
                        ? mapping.get("value") : mapping.get("staticValue"));
            }
        }
        Object staticValues = firstValue(config, "staticValues", "values", "data");
        if (staticValues instanceof Map<?, ?> values) {
            values.forEach((field, value) -> {
                if (field != null && StringUtils.isNotBlank(String.valueOf(field))) {
                    result.put(String.valueOf(field), value);
                }
            });
        }
        return result;
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : StringUtils.trimToEmpty(String.valueOf(value));
    }

    private String upper(String value) {
        return StringUtils.defaultString(value).trim().toUpperCase(Locale.ROOT);
    }
}
