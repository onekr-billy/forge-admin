package com.mdframe.forge.plugin.capability.flowaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.capability.flowaction.enums.CapabilityExecuteStatus;
import com.mdframe.forge.plugin.capability.flowaction.mapper.FlowProcessSystemServiceMapper;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityDefinition;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServicePublication;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceRegistrationSource;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class FlowProcessStartSystemService implements SystemServiceCapabilityDefinition {

    public static final String SERVICE_CODE = "flow.process.start";
    public static final String DEFINITION_VERSION = "1";
    public static final String PLATFORM_PERMISSION = "ai:capability:flow-action:invoke";

    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Set<String> VARIABLE_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> RESERVED_VARIABLES = Set.of(
            "tenantid", "userid", "activeorgid", "initiator", "startuserid",
            "startusername", "startdeptid", "startdeptname", "startuserroleids",
            "startuseractiveorgid", "startuserorgids", "regioncode", "startuserregioncode",
            "modelkey", "modelid", "deploymentid", "processdefinitionid", "businesstype",
            "businesskey", "flowbusinesskey", "processtitle");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("businessKey", "title", "variables");
    private static final Set<String> PUBLICATION_PARAMETER_FIELDS = Set.of("modelId", "variables");
    private static final Set<String> VARIABLE_DEFINITION_FIELDS = Set.of(
            "name", "type", "description", "required");

    private final FlowProcessSystemServiceMapper mapper;
    private final FlowClient flowClient;
    private final ObjectMapper objectMapper;

    public FlowProcessStartSystemService(
            FlowProcessSystemServiceMapper mapper,
            FlowClient flowClient,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.flowClient = flowClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String serviceCode() {
        return SERVICE_CODE;
    }

    @Override
    public String definitionVersion() {
        return DEFINITION_VERSION;
    }

    @Override
    public String platformPermission() {
        return PLATFORM_PERMISSION;
    }

    @Override
    public SystemServiceRegistrationSource registrationSource(Long tenantId) {
        List<FlowProcessModelSource> models = mapper.selectPublishedModels(requireTenant(tenantId));
        ObjectNode options = objectMapper.createObjectNode();
        ArrayNode modelOptions = options.putArray("models");
        for (FlowProcessModelSource model : models) {
            ObjectNode item = modelOptions.addObject();
            item.put("modelId", model.modelId());
            item.put("modelKey", model.modelKey());
            item.put("modelName", model.modelName());
            item.put("modelVersion", model.modelVersion());
            item.put("description", StringUtils.defaultString(model.description()));
        }
        ArrayNode types = options.putArray("variableTypes");
        VARIABLE_TYPES.stream().sorted().forEach(types::add);
        return new SystemServiceRegistrationSource(
                SERVICE_CODE, "启动已发布流程", "固定一个已发布流程模型，供外部系统安全发起流程。",
                DEFINITION_VERSION, "USER", "MEDIUM", publishParameterSchema(), options);
    }

    @Override
    public SystemServicePublication preparePublication(Long tenantId, JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) {
            throw new BusinessException("系统服务发布参数必须是 JSON 对象");
        }
        requireAllowedFields(
                parameters,
                PUBLICATION_PARAMETER_FIELDS,
                "系统服务发布参数包含未允许的身份、模型或扩展字段");
        String modelId = StringUtils.trimToNull(parameters.path("modelId").asText());
        if (modelId == null) {
            throw new BusinessException("请选择一个已发布流程模型");
        }
        FlowProcessModelSource model = mapper.selectPublishedModel(requireTenant(tenantId), modelId);
        if (model == null) {
            throw new BusinessException("所选流程模型未发布、已停用或版本不可用");
        }
        List<FlowProcessVariableDefinition> variables = variableDefinitions(parameters.path("variables"));
        ObjectNode policy = publicationPolicy(model, variables);
        return new SystemServicePublication(
                "启动" + model.modelName(),
                "以可信用户委托身份启动已发布流程“" + model.modelName() + "”。",
                inputSchema(variables), outputSchema(), policy);
    }

    @Override
    public Map<String, Object> prepareInput(Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        if (!PAYLOAD_FIELDS.containsAll(request.keySet())) {
            throw new BusinessException("请求包含未允许的身份、模型或顶层字段");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String businessKey = StringUtils.trimToNull(text(request.get("businessKey")));
        if (businessKey != null) {
            result.put("businessKey", businessKey);
        }
        String title = StringUtils.trimToNull(text(request.get("title")));
        if (title != null) {
            result.put("title", title);
        }
        result.put("variables", mapValue(request.get("variables"), "variables 必须是对象"));
        return result;
    }

    @Override
    public void validate(SecureActionDescriptor descriptor, Map<String, Object> input) {
        ExecutionIdentity identity = requireIdentity();
        requireMatchingModel(identity.loginUser().getTenantId(), descriptor.policySnapshot());
        String businessKey = requireText(input.get("businessKey"), "businessKey");
        if (businessKey.length() > 128) {
            throw new BusinessException("businessKey 不能超过 128 个字符");
        }
        String title = StringUtils.trimToNull(text(input.get("title")));
        if (title != null && title.length() > 200) {
            throw new BusinessException("title 不能超过 200 个字符");
        }
        Map<String, Object> variables = mapValue(input.get("variables"), "variables 必须是对象");
        Set<String> allowed = jsonTextSet(descriptor.policySnapshot().path("allowedVariables"));
        if (!allowed.containsAll(variables.keySet())) {
            throw new BusinessException("variables 包含发布版本未允许的流程变量");
        }
        Set<String> required = jsonTextSet(descriptor.policySnapshot().path("requiredVariables"));
        if (!variables.keySet().containsAll(required)) {
            throw new BusinessException("variables 缺少发布版本要求的必填流程变量");
        }
    }

    @Override
    public Map<String, Object> execute(
            SecureActionDescriptor descriptor,
            Map<String, Object> input,
            String requestId) {
        validate(descriptor, input);
        JsonNode policy = descriptor.policySnapshot();
        String modelKey = policy.path("modelKey").asText();
        String businessKey = requireText(input.get("businessKey"), "businessKey");
        String title = StringUtils.defaultIfBlank(
                StringUtils.trimToNull(text(input.get("title"))),
                policy.path("modelName").asText() + " - " + businessKey);
        Map<String, Object> variables = new LinkedHashMap<>(
                mapValue(input.get("variables"), "variables 必须是对象"));
        FlowResult<String> result = flowClient.startProcessForDelegatedUser(
                modelKey, businessKey, "external-system-service", title, variables);
        if (result == null || !result.isSuccess() || StringUtils.isBlank(result.getData())) {
            throw new BusinessException("流程启动失败: "
                    + (result == null ? "流程服务无返回" : result.getMsg()));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("executeStatus", CapabilityExecuteStatus.SUCCESS.getCode());
        output.put("message", "流程已启动");
        output.put("processInstanceId", result.getData());
        output.put("businessKey", businessKey);
        output.put("correlationId", requestId);
        output.put("idempotentHit", false);
        return output;
    }

    private ObjectNode publishParameterSchema() {
        ObjectNode root = schemaObject();
        ObjectNode properties = root.putObject("properties");
        properties.putObject("modelId").put("type", "string").put("description", "已发布流程模型 ID");
        ObjectNode variables = properties.putObject("variables");
        variables.put("type", "array").put("maxItems", 50)
                .put("description", "显式允许外围系统传入的流程变量定义");
        ObjectNode item = variables.putObject("items");
        item.put("type", "object").put("additionalProperties", false);
        ObjectNode itemProperties = item.putObject("properties");
        itemProperties.putObject("name").put("type", "string");
        itemProperties.putObject("type").put("type", "string");
        itemProperties.putObject("description").put("type", "string");
        itemProperties.putObject("required").put("type", "boolean");
        item.putArray("required").add("name").add("type");
        root.putArray("required").add("modelId");
        return root;
    }

    private ObjectNode inputSchema(List<FlowProcessVariableDefinition> definitions) {
        ObjectNode root = schemaObject();
        ObjectNode properties = root.putObject("properties");
        properties.putObject("businessKey")
                .put("type", "string").put("minLength", 1).put("maxLength", 128)
                .put("description", "外围业务唯一键；同一租户内重复调用会复用活动流程实例");
        properties.putObject("title")
                .put("type", "string").put("maxLength", 200)
                .put("description", "可选流程标题；为空时由平台生成");
        ObjectNode variables = properties.putObject("variables");
        variables.put("type", "object").put("additionalProperties", false)
                .put("description", "仅允许发布版本显式声明的流程变量");
        ObjectNode variableProperties = variables.putObject("properties");
        ArrayNode requiredVariables = variables.putArray("required");
        for (FlowProcessVariableDefinition definition : definitions) {
            ObjectNode property = variableProperties.putObject(definition.name());
            property.put("type", definition.type());
            property.put("description", StringUtils.defaultIfBlank(
                    definition.description(), "流程变量 " + definition.name()));
            if (definition.required()) {
                requiredVariables.add(definition.name());
            }
        }
        root.putArray("required").add("businessKey").add("variables");
        return root;
    }

    private ObjectNode outputSchema() {
        ObjectNode root = schemaObject();
        ObjectNode properties = root.putObject("properties");
        properties.putObject("executeStatus").put("type", "string").put("description", "执行状态");
        properties.putObject("message").put("type", "string").put("description", "执行结果说明");
        properties.putObject("processInstanceId").put("type", "string").put("description", "流程实例 ID");
        properties.putObject("businessKey").put("type", "string").put("description", "外围业务唯一键");
        properties.putObject("correlationId").put("type", "string").put("description", "调用追踪标识");
        properties.putObject("idempotentHit").put("type", "boolean").put("description", "是否命中幂等结果");
        root.putArray("required").add("executeStatus").add("message")
                .add("processInstanceId").add("businessKey").add("correlationId").add("idempotentHit");
        return root;
    }

    private ObjectNode publicationPolicy(
            FlowProcessModelSource model,
            List<FlowProcessVariableDefinition> definitions) {
        ObjectNode policy = objectMapper.createObjectNode();
        policy.put("modelId", model.modelId());
        policy.put("modelKey", model.modelKey());
        policy.put("modelName", model.modelName());
        policy.put("modelVersion", model.modelVersion());
        policy.put("deploymentId", model.deploymentId());
        policy.put("processDefinitionId", model.processDefinitionId());
        policy.put("permission", "ai:businessFlow:start");
        ArrayNode allowed = policy.putArray("allowedVariables");
        definitions.stream().map(FlowProcessVariableDefinition::name).forEach(allowed::add);
        ArrayNode required = policy.putArray("requiredVariables");
        definitions.stream().filter(FlowProcessVariableDefinition::required)
                .map(FlowProcessVariableDefinition::name).forEach(required::add);
        ObjectNode documentation = policy.putObject("documentation");
        documentation.putArray("businessRules")
                .add("流程模型、版本和部署信息在能力发布时固定，运行时不接受调用方指定。")
                .add("发起人、租户和活动组织来自 OAuth USER 委托身份，SERVICE/HMAC 调用会被拒绝。")
                .add("执行前重新校验流程仍处于已发布状态，版本或部署漂移时不会创建流程实例。")
                .add("同一租户和 businessKey 的活动流程只启动一次，重复调用复用原流程实例。");
        documentation.putArray("requestNotes")
                .add("Idempotency-Key 通过 Header 传入，不属于请求 Body。")
                .add("variables 只能包含能力发布版本明确允许的变量。")
                .add("tenantId、userId、activeOrgId、initiator、modelKey 等字段禁止传入。");
        documentation.putArray("responseNotes")
                .add("processInstanceId 可用于平台流程监控与后续业务关联。")
                .add("排障时请提供 correlationId/requestId，不要提供 Token 或客户端密钥。");
        return policy;
    }

    private List<FlowProcessVariableDefinition> variableDefinitions(JsonNode values) {
        if (values == null || values.isMissingNode() || values.isNull()) {
            return List.of();
        }
        if (!values.isArray() || values.size() > 50) {
            throw new BusinessException("流程变量定义必须是最多 50 项的数组");
        }
        List<FlowProcessVariableDefinition> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode item : values) {
            if (!item.isObject()) {
                throw new BusinessException("每个流程变量定义都必须是 JSON 对象");
            }
            requireAllowedFields(
                    item,
                    VARIABLE_DEFINITION_FIELDS,
                    "流程变量定义包含未允许的扩展字段");
            String name = StringUtils.trimToNull(item.path("name").asText());
            String type = StringUtils.defaultString(item.path("type").asText())
                    .trim().toLowerCase(Locale.ROOT);
            if (name == null || !VARIABLE_NAME.matcher(name).matches()
                    || RESERVED_VARIABLES.contains(name.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("流程变量名称无效或属于平台保留字段: " + name);
            }
            if (!VARIABLE_TYPES.contains(type)) {
                throw new BusinessException("流程变量类型只支持 string/integer/number/boolean/object/array");
            }
            if (!names.add(name)) {
                throw new BusinessException("流程变量名称重复: " + name);
            }
            String description = StringUtils.trimToNull(item.path("description").asText());
            if (description != null && description.length() > 200) {
                throw new BusinessException("流程变量说明不能超过 200 个字符");
            }
            result.add(new FlowProcessVariableDefinition(
                    name, type, description, item.path("required").asBoolean(false)));
        }
        return List.copyOf(result);
    }

    private void requireMatchingModel(Long tenantId, JsonNode policy) {
        String modelId = policy.path("modelId").asText();
        FlowProcessModelSource current = mapper.selectPublishedModel(requireTenant(tenantId), modelId);
        if (current == null
                || !Objects.equals(current.modelKey(), policy.path("modelKey").asText())
                || !Objects.equals(current.modelVersion(), policy.path("modelVersion").asInt())
                || !Objects.equals(current.deploymentId(), policy.path("deploymentId").asText())
                || !Objects.equals(
                        current.processDefinitionId(), policy.path("processDefinitionId").asText())) {
            throw new BusinessException(409, "FLOW_MODEL_SNAPSHOT_MISMATCH");
        }
    }

    private ExecutionIdentity requireIdentity() {
        ExecutionIdentity identity = ExecutionIdentityContextHolder.current()
                .orElseThrow(() -> new BusinessException(401, "缺少可信能力执行身份"));
        if (!"USER".equals(identity.actorType())
                || identity.actorUserId() == null
                || identity.loginUser().getTenantId() == null
                || identity.loginUser().getActiveOrgId() == null) {
            throw new BusinessException(403, "USER_DELEGATION_REQUIRED");
        }
        return identity;
    }

    private ObjectNode schemaObject() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("$schema", CapabilitySchemaValidator.DRAFT_2020_12);
        root.put("type", "object");
        root.put("additionalProperties", false);
        return root;
    }

    private Set<String> jsonTextSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    private Map<String, Object> mapValue(Object value, String message) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new BusinessException(message);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String requireText(Object value, String field) {
        String text = StringUtils.trimToNull(text(value));
        if (text == null) {
            throw new BusinessException(field + " 不能为空");
        }
        return text;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    private void requireAllowedFields(JsonNode object, Set<String> allowedFields, String message) {
        if (!allowedFields.containsAll(object.properties().stream().map(Map.Entry::getKey).toList())) {
            throw new BusinessException(message);
        }
    }
}
