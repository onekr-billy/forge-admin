package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.controlplane.audit.CapabilityActorType;
import com.mdframe.forge.plugin.capability.model.CapabilityRiskLevel;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** External start uses the exact same application orchestration as the runtime page button. */
@RequiredArgsConstructor
public class ApplicationProcessStartSystemService implements SystemServiceCapabilityDefinition {
    public static final String CODE = "lowcode.business-process.start";
    private static final String START_PERMISSION = "ai:businessProcess:start";
    private final ApplicationProcessCapabilitySource sources;
    private final BusinessProcessOrchestrator orchestrator;
    private final DynamicCrudService records;
    private final ObjectMapper mapper;
    private final CapabilitySchemaValidator validator;

    @Override public String serviceCode() { return CODE; }
    @Override public String definitionVersion() { return "1"; }
    @Override public String platformPermission() { return "ai:capability:system-service:invoke"; }

    @Override
    public SystemServiceRegistrationSource registrationSource(Long tenant) {
        ApplicationProcessCapabilitySource.requireTenant(tenant);
        return registration(mapper.createObjectNode().put("registrationKind", "APPLICATION_PROCESS"));
    }

    @Override
    public SystemServiceRegistrationSource registrationSource(Long tenant, SystemServiceRegistrationContext context) {
        ObjectNode options = mapper.createObjectNode().put("registrationKind", "APPLICATION_PROCESS");
        options.set("processes", mapper.valueToTree(sources.options(tenant, context.applicationId(), context.objectId())));
        return registration(options);
    }

    private SystemServiceRegistrationSource registration(ObjectNode options) {
        return new SystemServiceRegistrationSource(CODE, "应用业务流程", "执行应用已发布的完整业务流程，包括审批、条件与业务动作。",
                definitionVersion(), CapabilityActorType.USER.name(), CapabilityRiskLevel.HIGH.name(), object(), options);
    }

    @Override
    public SystemServicePublication preparePublication(Long tenant, JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) throw new BusinessException("请选择应用业务流程");
        parameters.fieldNames().forEachRemaining(key -> {
            if (!Set.of("applicationId", "objectId", "processCode").contains(key)) {
                throw new BusinessException("未知业务流程发布参数");
            }
        });
        var source = sources.require(tenant, ApplicationProcessCapabilitySource.positiveId(parameters.path("applicationId")),
                ApplicationProcessCapabilitySource.positiveId(parameters.path("objectId")), parameters.path("processCode").asText());
        ObjectNode input = object().put("$schema", CapabilitySchemaValidator.DRAFT_2020_12);
        input.putObject("properties").putObject("recordId").put("type", "string").put("minLength", 1).put("maxLength", 128)
                .put("description", "应用表单中已保存的业务记录 ID；执行对应的完整业务流程");
        input.putArray("required").add("recordId");
        ObjectNode policy = mapper.valueToTree(source);
        policy.set("registrationParameters", parameters.deepCopy());
        policy.putObject("documentation").putArray("businessRules")
                .add("使用用户委托身份；只接受已保存的 recordId，按用户数据范围检查记录。")
                .add("复用应用业务流程启动链路，不依赖旧对象主流程绑定，也不直接启动独立审批模型。")
                .add("相同流程版本和记录复用原运行；失败后须在业务流程运行记录中按原权限处理重试。")
                .add("应用或业务流程版本变化后须审核并发布能力新版本；不接受外部身份、模型或流程变量覆盖。");
        ObjectNode output = object().put("$schema", CapabilitySchemaValidator.DRAFT_2020_12);
        ObjectNode fields = output.putObject("properties");
        for (String name : Set.of("runId", "processCode", "processVersionId", "recordId", "status")) {
            fields.putObject(name).put("type", "string");
            output.withArray("required").add(name);
        }
        return new SystemServicePublication(source.processName() + " · 发起业务流程", "对已有记录发起 " + source.processName(), input, output, policy);
    }

    @Override
    public Map<String, Object> prepareInput(Map<String, Object> payload) {
        if (payload == null || !Set.of("recordId").containsAll(payload.keySet())) {
            throw new BusinessException("业务流程输入只允许 recordId；应用、流程及用户由服务端固定");
        }
        return new LinkedHashMap<>(payload);
    }

    @Override public void validate(SecureActionDescriptor descriptor, Map<String, Object> input) { validated(descriptor, input); }

    private ApplicationProcessCapabilitySource.Source validated(SecureActionDescriptor descriptor, Map<String, Object> input) {
        var identity = ExecutionIdentityContextHolder.current().orElseThrow(() -> new BusinessException(403, "USER_DELEGATION_REQUIRED"));
        if (!CapabilityActorType.USER.name().equals(identity.actorType()) || identity.actorUserId() == null
                || identity.loginUser().getTenantId() == null || identity.loginUser().getActiveOrgId() == null) {
            throw new BusinessException(403, "USER_DELEGATION_REQUIRED");
        }
        JsonNode policy = descriptor.policySnapshot();
        var source = sources.require(identity.loginUser().getTenantId(),
                ApplicationProcessCapabilitySource.positiveId(policy.path("applicationId")),
                ApplicationProcessCapabilitySource.positiveId(policy.path("objectId")), policy.path("processCode").asText());
        JsonNode expected = mapper.valueToTree(source);
        for (String field : Set.of("applicationCode", "applicationVersion", "objectCode", "configKey", "processId", "processVersionId", "schemaHash", "permission")) {
            if (!expected.path(field).equals(policy.path(field))) throw new BusinessException(409, "APPLICATION_PROCESS_SOURCE_CHANGED");
        }
        if (!source.permission().equals(descriptor.permission()) || !SessionHelper.hasPermission(START_PERMISSION)
                || !SessionHelper.hasPermission(source.permission())) throw new BusinessException(403, "没有权限启动该业务流程");
        Map<String, Object> body = new LinkedHashMap<>(input);
        body.remove("idempotencyKey");
        validator.validateInstance(descriptor.inputSchema(), mapper.valueToTree(body));
        if (!(body.get("recordId") instanceof String id) || id.isBlank() || id.length() > 128 || !id.equals(id.trim())) {
            throw new BusinessException("recordId 必须是有效的业务记录 ID 字符串");
        }
        if (records.selectById(source.configKey(), id) == null) throw new BusinessException(403, "记录不存在或不在当前用户可访问范围内");
        return source;
    }

    @Override
    public Map<String, Object> execute(SecureActionDescriptor descriptor, Map<String, Object> input, String requestId) {
        var source = validated(descriptor, input);
        if (Objects.toString(input.get("idempotencyKey"), "").isBlank()) throw new BusinessException("missing_idempotency_key");
        BusinessProcessManualStartDTO command = new BusinessProcessManualStartDTO();
        command.setRecordId((String) input.get("recordId"));
        command.setObjectCode(source.objectCode());
        var run = orchestrator.startPublished(source.applicationCode(), source.processCode(), command, source.processVersionId());
        return Map.of("runId", run.getId(), "processCode", run.getProcessCode(), "processVersionId", run.getProcessVersionId(),
                "recordId", run.getSubjectRecordId(), "status", run.getStatus());
    }

    private ObjectNode object() { return mapper.createObjectNode().put("type", "object").put("additionalProperties", false); }
}
