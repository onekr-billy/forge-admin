package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final BusinessApplicationObjectMapper applicationObjectMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final DynamicCrudService dynamicCrudService;

    /** 动作使用独立事务，避免下游事务异常把编排状态事务标记为 rollback-only。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String execute(AiBusinessProcessRun run,
                          BusinessProcessSchema schema,
                          BusinessProcessNode node) {
        Map<String, Object> config = node.getConfig() == null ? Map.of() : node.getConfig();
        String actionType = upper(text(config.get("actionType")));
        String configuredObjectCode = text(config.get("objectCode"));
        // Older action-node drafts used the generic placeholder
        // `business_object`. It is not a runtime object and must never be
        // treated as the final published CRUD key; resolve the immutable
        // object metadata first, then use its canonical object/config key.
        String objectCode = firstConcreteObjectCode(
                configuredObjectCode,
                schema.getSubject() == null ? null : schema.getSubject().getObjectCode(),
                run.getSubjectObjectCode());
        AiBusinessObject targetObject = resolveTargetObject(run, schema, config, objectCode);
        if (targetObject != null) {
            objectCode = StringUtils.defaultIfBlank(targetObject.getObjectCode(), objectCode);
        }
        AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(run.getTenantId(), objectCode, targetObject);
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

    private AiBusinessObject resolveTargetObject(AiBusinessProcessRun run,
                                                 BusinessProcessSchema schema,
                                                 Map<String, Object> config,
                                                 String objectCode) {
        String objectId = firstText(config, "targetObjectId", "objectId");
        if (StringUtils.isNotBlank(objectId)) {
            try {
                AiBusinessObject object = businessObjectMapper.selectById(Long.valueOf(objectId));
                if (object != null) {
                    return object;
                }
            } catch (NumberFormatException ignored) {
                // 雪花 ID 只允许无损字符串传递；无法解析时继续走编码兼容路径。
            }
        }
        String configKey = firstText(config, "targetConfigKey", "configKey");
        if (StringUtils.isNotBlank(configKey)) {
            AiBusinessObject object = businessObjectMapper.selectByConfigKey(run.getTenantId(), configKey);
            if (object != null) {
                return object;
            }
        }
        if (StringUtils.isNotBlank(objectCode) && !"business_object".equalsIgnoreCase(objectCode)) {
            AiBusinessObject object = businessObjectMapper.selectFirstByObjectCode(run.getTenantId(), objectCode);
            if (object != null) {
                return object;
            }
        }
        if (schema.getSubject() != null && "business_object".equalsIgnoreCase(objectCode)) {
            objectId = StringUtils.trimToEmpty(schema.getSubject().getObjectId());
            if (StringUtils.isNotBlank(objectId)) {
                try {
                    return businessObjectMapper.selectById(Long.valueOf(objectId));
                } catch (NumberFormatException ignored) {
                    // 保持占位符兼容，后续仍可按 subject objectCode 查询。
                }
            }
        }
        if (run.getApplicationId() != null) {
            List<BusinessApplicationObjectVO> objects = applicationObjectMapper.selectByApplicationId(
                    run.getTenantId(), run.getApplicationId());
            BusinessApplicationObjectVO primary = objects == null ? null : objects.stream()
                    .filter(item -> item != null && "PRIMARY".equalsIgnoreCase(item.getObjectRole()))
                    .findFirst()
                    .orElse(objects.stream().filter(item -> item != null).findFirst().orElse(null));
            if (primary != null && primary.getObjectId() != null) {
                AiBusinessObject object = businessObjectMapper.selectById(primary.getObjectId());
                if (object != null) {
                    return object;
                }
            }
        }
        return null;
    }

    private AiCrudConfig resolvePublishedRuntimeConfig(Long tenantId,
                                                        String objectCode,
                                                        AiBusinessObject targetObject) {
        AiCrudConfig runtimeConfig = crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(tenantId, objectCode);
        if (runtimeConfig == null && targetObject != null
                && StringUtils.isNotBlank(targetObject.getConfigKey())
                && !StringUtils.equals(targetObject.getConfigKey(), objectCode)) {
            runtimeConfig = crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(
                    tenantId, targetObject.getConfigKey());
        }
        return runtimeConfig;
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
