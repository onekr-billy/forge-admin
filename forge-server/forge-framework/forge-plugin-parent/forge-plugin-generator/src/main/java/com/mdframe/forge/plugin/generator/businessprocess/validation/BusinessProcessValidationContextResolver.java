package com.mdframe.forge.plugin.generator.businessprocess.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessPermissionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessFlowModelVO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessageTemplate;
import com.mdframe.forge.plugin.message.service.MessageTemplateService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从当前租户和应用的真实资产构造业务流程校验目录。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessProcessValidationContextResolver {

    private final ObjectMapper objectMapper;
    private final BusinessApplicationObjectMapper applicationObjectMapper;
    private final BusinessObjectDesignVersionMapper objectDesignVersionMapper;
    private final BusinessPermissionMapper permissionMapper;
    private final BusinessProcessVersionMapper processVersionMapper;
    private final BusinessFlowService businessFlowService;
    private final ObjectProvider<FlowClient> flowClientProvider;
    private final ObjectProvider<MessageTemplateService> messageTemplateServiceProvider;
    private final BusinessProcessSchemaValidator schemaValidator;

    public BusinessProcessValidationContext resolve(Long tenantId,
                                                    Long applicationId,
                                                    String expectedProcessCode,
                                                    BusinessProcessSchema schema) {
        if (tenantId == null || tenantId <= 0 || applicationId == null || applicationId <= 0) {
            throw new BusinessException("缺少有效的租户或业务应用上下文");
        }
        List<BusinessApplicationObjectVO> objects = safeList(
                applicationObjectMapper.selectByApplicationId(tenantId, applicationId)).stream()
                .filter(object -> object != null
                        && object.getObjectId() != null
                        && StringUtils.isNotBlank(object.getObjectCode())
                        && Integer.valueOf(1).equals(object.getObjectStatus()))
                .toList();

        Map<String, String> objectIdsByCode = new LinkedHashMap<>();
        Map<Long, BusinessApplicationObjectVO> objectsById = new LinkedHashMap<>();
        for (BusinessApplicationObjectVO object : objects) {
            objectIdsByCode.put(object.getObjectCode(), String.valueOf(object.getObjectId()));
            objectsById.put(object.getObjectId(), object);
        }

        Map<Long, AiBusinessObjectDesignVersion> publishedVersions = loadPublishedObjectVersions(
                tenantId, objectsById.keySet());
        Map<String, Set<String>> fieldsByObjectCode = new LinkedHashMap<>();
        Map<String, String> publishedObjectVersionIdsByCode = new LinkedHashMap<>();
        Set<String> availableBusinessActionCodes = new LinkedHashSet<>();
        for (BusinessApplicationObjectVO object : objects) {
            AiBusinessObjectDesignVersion published = publishedVersions.get(object.getObjectId());
            String fieldSnapshot = published != null && StringUtils.isNotBlank(published.getModelSnapshot())
                    ? published.getModelSnapshot() : object.getModelSchema();
            Set<String> fields = extractFields(fieldSnapshot);
            fieldsByObjectCode.put(object.getObjectCode(), fields);
            if (published != null) {
                publishedObjectVersionIdsByCode.put(
                        object.getObjectCode(), String.valueOf(published.getId()));
                availableBusinessActionCodes.addAll(extractPublishedActionCodes(
                        published.getDesignerOptionsSnapshot()));
            }
        }

        BusinessProcessValidationContext context = new BusinessProcessValidationContext()
                .setExpectedProcessCode(expectedProcessCode)
                .setObjectIdsByCode(objectIdsByCode)
                .setFieldsByObjectCode(fieldsByObjectCode)
                .setPublishedObjectVersionIdsByCode(publishedObjectVersionIdsByCode)
                .setAvailableBusinessActionCodes(availableBusinessActionCodes)
                .setKnownPermissions(resolveKnownPermissions(tenantId, schema))
                .setCapabilityBridgeAvailable(false);

        if (schema == null) {
            return context;
        }
        resolveFlowModels(schema, context);
        resolveFormAssets(schema, objects, context);
        resolveMessageTemplates(schema, context);
        resolvePublishedSubProcesses(tenantId, applicationId, context);
        return context;
    }

    /**
     * 返回当前租户有权引用且能够通过发布检查的审批模型目录。
     */
    public List<BusinessProcessFlowModelVO> resolveAvailableFlowModels(Long tenantId, Long applicationId) {
        if (tenantId == null || tenantId <= 0 || applicationId == null || applicationId <= 0) {
            throw new BusinessException("缺少有效的租户或业务应用上下文");
        }
        // Flowable 模型是租户级可复用审批资产，不能通过主对象 FLOW Binding
        // 反向推导应用流程的可选目录。
        return resolveDesignableFlowModels();
    }

    public List<BusinessProcessFlowModelVO> resolveDesignableFlowModels() {
        return loadFlowModelCatalog(false);
    }

    private Map<Long, AiBusinessObjectDesignVersion> loadPublishedObjectVersions(
            Long tenantId, Collection<Long> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AiBusinessObjectDesignVersion> result = new LinkedHashMap<>();
        for (AiBusinessObjectDesignVersion version : safeList(
                objectDesignVersionMapper.selectLatestPublishedVersions(
                        tenantId, new ArrayList<>(objectIds)))) {
            if (version != null && version.getObjectId() != null) {
                result.putIfAbsent(version.getObjectId(), version);
            }
        }
        return result;
    }

    private Set<String> extractFields(String modelSchemaJson) {
        if (StringUtils.isBlank(modelSchemaJson)) {
            return new LinkedHashSet<>();
        }
        try {
            LowcodeModelSchema modelSchema = objectMapper.readValue(modelSchemaJson, LowcodeModelSchema.class);
            Set<String> result = new LinkedHashSet<>();
            for (LowcodeFieldSchema field : safeList(modelSchema.getFields())) {
                if (field != null
                        && StringUtils.isNotBlank(field.getField())
                        && !"DISABLED".equalsIgnoreCase(field.getFieldStatus())) {
                    result.add(field.getField().trim());
                }
            }
            return result;
        } catch (Exception exception) {
            log.warn("业务流程校验无法解析业务对象字段发布快照: errorType={}",
                    exception.getClass().getSimpleName());
            return new LinkedHashSet<>();
        }
    }

    private Set<String> extractPublishedActionCodes(String designerOptionsJson) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.isBlank(designerOptionsJson)) {
            return result;
        }
        try {
            JsonNode actions = objectMapper.readTree(designerOptionsJson).path("actions");
            if (!actions.isArray()) {
                return result;
            }
            for (JsonNode action : actions) {
                String actionCode = StringUtils.trimToNull(action.path("actionCode").asText(null));
                int status = action.hasNonNull("status") ? action.path("status").asInt(1) : 1;
                if (actionCode != null && status == 1) {
                    result.add(actionCode);
                }
            }
        } catch (Exception exception) {
            log.warn("业务流程校验无法解析业务动作发布快照: errorType={}",
                    exception.getClass().getSimpleName());
            return new LinkedHashSet<>();
        }
        return result;
    }

    private void resolveFlowModels(BusinessProcessSchema schema,
                                   BusinessProcessValidationContext context) {
        Set<String> required = new LinkedHashSet<>(safeList(schema.getDependencies().getFlowModels()));
        Set<String> available = resolveFlowModelCatalog().stream()
                .map(BusinessProcessFlowModelVO::getModelKey)
                .filter(required::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        context.setAvailableFlowModelKeys(available);
    }

    private List<BusinessProcessFlowModelVO> resolveFlowModelCatalog() {
        return loadFlowModelCatalog(true);
    }

    private List<BusinessProcessFlowModelVO> loadFlowModelCatalog(boolean publishedOnly) {
        FlowClient flowClient = flowClientProvider.getIfAvailable();
        if (flowClient == null) {
            return List.of();
        }
        try {
            FlowResult<List<Map<String, Object>>> response = flowClient.getModelList(null, publishedOnly ? 1 : null);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return List.of();
            }
            List<BusinessProcessFlowModelVO> result = new ArrayList<>();
            for (Map<String, Object> model : response.getData()) {
                if (publishedOnly && !isPublishedFlowModel(model)) {
                    continue;
                }
                String designerType = StringUtils.trimToNull(text(model.get("designerType")));
                if ("business".equalsIgnoreCase(designerType)) {
                    continue;
                }
                BusinessProcessFlowModelVO item = new BusinessProcessFlowModelVO();
                item.setModelId(StringUtils.firstNonBlank(text(model.get("modelId")), text(model.get("id"))));
                item.setModelKey(StringUtils.firstNonBlank(text(model.get("modelKey")), text(model.get("key"))));
                if (StringUtils.isBlank(item.getModelKey())) {
                    continue;
                }
                item.setModelName(StringUtils.firstNonBlank(
                        text(model.get("modelName")), text(model.get("name")), item.getModelKey()));
                item.setStatus(integer(model.get("status")));
                item.setVersion(integer(model.get("version")));
                item.setProcessDefinitionId(text(model.get("processDefinitionId")));
                item.setDeploymentId(text(model.get("deploymentId")));
                item.setDeployed(isPublishedFlowModel(model));
                result.add(item);
            }
            result.sort(Comparator.comparing(BusinessProcessFlowModelVO::getModelName,
                    Comparator.nullsLast(String::compareToIgnoreCase)));
            return result;
        } catch (Exception exception) {
            log.debug("业务流程校验无法读取租户审批模型目录", exception);
            return List.of();
        }
    }

    private boolean isPublishedFlowModel(Map<String, Object> model) {
        if (model == null || model.isEmpty()) {
            return false;
        }
        Integer status = integer(model.get("status"));
        Integer version = integer(model.get("version"));
        return Integer.valueOf(1).equals(status)
                && version != null
                && version > 0
                && StringUtils.isNotBlank(text(model.get("processDefinitionId")))
                && StringUtils.isNotBlank(text(model.get("deploymentId")));
    }

    private void resolveFormAssets(BusinessProcessSchema schema,
                                   List<BusinessApplicationObjectVO> objects,
                                   BusinessProcessValidationContext context) {
        Set<String> required = new LinkedHashSet<>(safeList(schema.getDependencies().getFormAssets()));
        if (required.isEmpty()) {
            return;
        }
        Set<String> available = new LinkedHashSet<>();
        String subjectCode = schema.getSubject() == null ? null : schema.getSubject().getObjectCode();
        List<String> objectCodes = objects.stream()
                .map(BusinessApplicationObjectVO::getObjectCode)
                .sorted((left, right) -> {
                    if (left.equals(subjectCode)) {
                        return -1;
                    }
                    if (right.equals(subjectCode)) {
                        return 1;
                    }
                    return left.compareTo(right);
                })
                .toList();
        for (String objectCode : objectCodes) {
            try {
                collectFormAssetKeys(businessFlowService.getFormAssets(objectCode), available);
            } catch (Exception exception) {
                log.debug("业务流程校验无法解析表单资产: objectCode={}", objectCode, exception);
            }
            if (available.containsAll(required)) {
                break;
            }
        }
        context.setAvailableFormAssetKeys(available);
    }

    private void collectFormAssetKeys(Map<String, Object> catalog, Set<String> result) {
        if (catalog == null) {
            return;
        }
        Object rawAssets = catalog.get("formAssets");
        if (!(rawAssets instanceof Collection<?> assets)) {
            return;
        }
        for (Object asset : assets) {
            if (asset instanceof Map<?, ?> map) {
                Object value = map.get("formKey");
                String formKey = value == null ? null : StringUtils.trimToNull(String.valueOf(value));
                if (formKey != null) {
                    result.add(formKey);
                }
            }
        }
    }

    private void resolveMessageTemplates(BusinessProcessSchema schema,
                                         BusinessProcessValidationContext context) {
        MessageTemplateService templateService = messageTemplateServiceProvider.getIfAvailable();
        if (templateService == null) {
            return;
        }
        Set<String> available = new LinkedHashSet<>();
        for (String templateCode : safeList(schema.getDependencies().getMessageTemplates())) {
            try {
                SysMessageTemplate template = templateService.getByCode(templateCode);
                if (template != null && Integer.valueOf(1).equals(template.getEnabled())) {
                    available.add(templateCode);
                }
            } catch (Exception exception) {
                log.debug("业务流程校验无法解析消息模板: templateCode={}", templateCode, exception);
                // 缺失或失效模板交给校验器报告，不回显底层异常。
            }
        }
        context.setAvailableMessageTemplateCodes(available);
    }

    private void resolvePublishedSubProcesses(Long tenantId,
                                              Long applicationId,
                                              BusinessProcessValidationContext context) {
        Set<String> publishedCodes = new LinkedHashSet<>();
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (AiBusinessProcessVersion version : safeList(
                processVersionMapper.selectCurrentPublishedByApplication(tenantId, applicationId))) {
            if (version == null || StringUtils.isBlank(version.getProcessCode())) {
                continue;
            }
            publishedCodes.add(version.getProcessCode());
            try {
                BusinessProcessSchema published = schemaValidator.normalize(version.getSchemaJson());
                dependencies.put(version.getProcessCode(), new LinkedHashSet<>(
                        safeList(published.getDependencies().getSubProcesses())));
            } catch (Exception exception) {
                log.warn("业务流程校验无法解析已发布子流程: processCode={}, errorType={}",
                        version.getProcessCode(), exception.getClass().getSimpleName());
                dependencies.put(version.getProcessCode(), Set.of());
            }
        }
        context.setPublishedSubProcessCodes(publishedCodes);
        context.setSubProcessDependencies(dependencies);
    }

    private Set<String> resolveKnownPermissions(Long tenantId, BusinessProcessSchema schema) {
        if (schema == null) {
            return new LinkedHashSet<>();
        }
        List<String> declared = safeList(schema.getNodes()).stream()
                .filter(node -> node != null && "START_MANUAL".equals(node.getType()))
                .map(BusinessProcessNode::getConfig)
                .map(config -> config == null ? null : StringUtils.trimToNull(text(config.get("permission"))))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (declared.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(safeList(
                permissionMapper.selectExistingPermissions(tenantId, declared)));
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
