package com.mdframe.forge.plugin.capability.controlplane.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityGrant;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityVersion;
import com.mdframe.forge.plugin.capability.controlplane.enums.CapabilityPublishStatus;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityCallGuideCheckVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 能力调用指南的发布契约策略。
 *
 * <p>以 Policy + Contract Builder 集中版本 Schema、说明、字段授权和流程绑定快照规则，
 * 调用指南服务只负责加载实体与编排最终视图。</p>
 */
final class CapabilityCallContractPolicy {

    private final ObjectMapper objectMapper;

    CapabilityCallContractPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode readVersionSchema(String json) {
        if (StringUtils.isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode schema = objectMapper.readTree(json);
            return schema != null && schema.isObject()
                    ? schema : objectMapper.createObjectNode();
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    List<String> textValues(JsonNode values) {
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach(value -> {
            String text = value.isTextual() ? StringUtils.trimToNull(value.asText()) : null;
            if (text != null) {
                result.add(text);
            }
        });
        return List.copyOf(result);
    }

    String actionCode(AiCapabilityVersion version) {
        if (version == null) {
            return null;
        }
        String operation = StringUtils.trimToNull(
                readPolicy(version.getPolicySnapshot()).path("operation").asText());
        if (operation != null) {
            return operation;
        }
        String[] source = StringUtils.defaultString(version.getSourceKey()).split("/", -1);
        return source.length == 3 ? StringUtils.trimToNull(source[2]) : null;
    }

    List<String> requestNotes(AiCapabilityVersion version, String actionCode) {
        if (version == null) {
            return List.of();
        }
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        JsonNode documented = readPolicy(version.getPolicySnapshot())
                .path("documentation").path("requestNotes");
        if (documented.isArray()) {
            documented.forEach(item -> {
                String note = item.isTextual() ? StringUtils.trimToNull(item.asText()) : null;
                if (note != null) {
                    notes.add(note);
                }
            });
        }
        if ("FLOW_ACTION".equals(version.getSourceType())) {
            if ("SUBMIT".equals(actionCode)) {
                notes.add("SUBMIT 直接创建业务申请并启动主流程，请按 data 字段说明提交申请内容，不需要也不能传 recordId。");
                notes.add("申请人、归属人、租户、组织、单据初始状态和流程发起人均从本次 Token 的可信委托用户生成。");
                notes.add("流程启动失败时必须复用原 Idempotency-Key 重试，平台会复用已经创建的申请记录。");
                return List.copyOf(notes);
            }
            String[] source = StringUtils.defaultString(version.getSourceKey()).split("/", -1);
            String objectCode = source.length == 3 ? source[1] : "当前业务对象";
            notes.add("recordId 必须填写业务对象「" + objectCode
                    + "」中已经保存的真实记录主键，不能填写任意测试数字。");
            notes.add("记录必须在本次 Token 对应的实际委托用户数据权限范围内；平台不会泄露记录究竟不存在还是不可见。");
            if ("START".equals(actionCode)) {
                notes.add("START 只为已有业务记录发起流程，不会创建业务记录；请先在业务页面保存记录，再复制记录 ID 调用。");
            } else {
                notes.add("APPROVE/REJECT 还必须填写属于该记录、该流程且由当前委托用户可办理的真实 taskId。");
            }
        }
        return List.copyOf(notes);
    }

    List<String> defaultResponseNotes(AiCapabilityVersion version) {
        if (version == null) {
            return List.of();
        }
        if ("FLOW_ACTION".equals(version.getSourceType())) {
            return List.of(
                    "processInstanceId 是本次启动或操作关联的流程实例 ID。",
                    "correlationId 或 requestId 用于在客户端工作台中定位调用日志。",
                    "idempotentHit=true 表示复用了相同幂等键的历史结果。");
        }
        return List.of(
                "返回字段以当前已发布版本的输出契约为准，外围系统应兼容新增的可选字段。",
                "correlationId 或 requestId 用于在客户端工作台中定位调用日志。",
                "失败响应中的 code、failureStage 和 schemaPath 可用于区分认证、授权、参数或业务执行问题。");
    }

    List<String> defaultBusinessRules(AiCapabilityVersion version, String actionCode) {
        if (version == null) {
            return List.of();
        }
        if ("FLOW_ACTION".equals(version.getSourceType())) {
            if ("SUBMIT".equals(actionCode)) {
                return List.of(
                        "申请数据按业务对象发布版本执行字段白名单、必填、类型、长度、字典值和数据库约束校验。",
                        "记录创建与流程启动使用同一幂等键编排；重试不会重复创建申请。",
                        "申请人、租户、组织、审计字段和流程发起人只从可信委托用户生成。",
                        "流程模型、主流程绑定、委托用户权限和数据权限在实际执行时重新校验。");
            }
            return List.of(
                    "recordId 必须是当前委托用户可见的已保存业务记录，平台不会泄露记录不存在还是无权访问。",
                    "流程模型、主流程绑定、业务记录状态、重复运行实例和办理权限在实际执行时重新校验。",
                    "产生写入的请求必须携带唯一 Idempotency-Key，超时重试时复用原值。");
        }
        if ("BUSINESS_ACTION".equals(version.getSourceType())) {
            return List.of(
                    "执行前校验客户端授权、调用主体类型、Forge 用户角色权限和业务动作权限。",
                    "输入按发布版本执行字段白名单、必填、数据类型和数据库约束校验。",
                    "只允许执行已发布的受控服务端步骤，打开页面等前端动作不能远程调用。",
                    "产生写入的请求必须携带唯一 Idempotency-Key，超时重试时复用原值。");
        }
        return List.of(
                "系统服务的入参、模型、变量白名单和运行时权限均以当前发布版本快照为准。",
                "调用前校验客户端授权、主体类型和实际执行用户权限。");
    }

    JsonNode prepareRequestExample(AiCapabilityVersion version,
                                   String actionCode,
                                   String grantFieldPolicy,
                                   JsonNode requestExample) {
        if (version == null || !(requestExample instanceof ObjectNode root)) {
            return requestExample;
        }
        Set<String> grantedFields = readTextSet(readPolicy(grantFieldPolicy).path("allowedFields"));
        if ("BUSINESS_ACTION".equals(version.getSourceType())) {
            retainExampleFields(root.path("arguments"), grantedFields);
            return root;
        }
        if (!"FLOW_ACTION".equals(version.getSourceType())) {
            return root;
        }
        if ("SUBMIT".equals(actionCode)) {
            retainExampleFields(root.path("data"), grantedFields);
            return root;
        }
        root.put("recordId", "<REAL_RECORD_ID>");
        if (!"START".equals(actionCode) && root.path("arguments") instanceof ObjectNode arguments) {
            arguments.put("taskId", "<REAL_TASK_ID>");
        }
        return root;
    }

    void addFlowBindingCheck(List<CapabilityCallGuideCheckVO> checks,
                             AiCapability capability,
                             AiCapabilityVersion resolvedVersion,
                             AiCapabilityVersion currentVersion,
                             String resolvedVersionNumber,
                             String currentVersionNumber) {
        if (!"FLOW_ACTION".equals(capability.getSourceType())) {
            return;
        }
        if (resolvedVersion == null || !CapabilityPublishStatus.PUBLISHED.matches(resolvedVersion.getStatus())) {
            add(checks, "FLOW_BINDING", "流程绑定", false, true,
                    "授权版本可用后才能核对流程绑定快照");
            return;
        }
        if (Objects.equals(resolvedVersionNumber, currentVersionNumber)) {
            add(checks, "FLOW_BINDING", "流程绑定", true, true,
                    "实际调用版本与能力当前版本的流程绑定一致");
            return;
        }
        if (currentVersion == null || !CapabilityPublishStatus.PUBLISHED.matches(currentVersion.getStatus())) {
            add(checks, "FLOW_BINDING", "流程绑定", false, true,
                    "能力当前版本不存在或未发布，无法核对流程绑定快照");
            return;
        }
        JsonNode resolvedPolicy = readPolicy(resolvedVersion.getPolicySnapshot());
        JsonNode currentPolicy = readPolicy(currentVersion.getPolicySnapshot());
        if (sameFlowBindingSnapshot(resolvedPolicy, currentPolicy)) {
            add(checks, "FLOW_BINDING", "流程绑定", true, true,
                    "客户端使用旧版本 v" + resolvedVersionNumber + "，但流程绑定快照仍与当前版本一致");
            return;
        }
        add(checks, "FLOW_BINDING", "流程绑定", false, true,
                "客户端授权仍使用 v" + resolvedVersionNumber
                        + "，能力当前 v" + currentVersionNumber
                        + " 已更新流程绑定；继续测试会返回 FLOW_BINDING_MISMATCH，请先切换到当前版本");
    }

    void addSubmissionGrantCheck(List<CapabilityCallGuideCheckVO> checks,
                                 AiCapabilityVersion version,
                                 String actionCode,
                                 AiCapabilityGrant grant) {
        if (version == null || !"FLOW_ACTION".equals(version.getSourceType())
                || !"SUBMIT".equals(actionCode)) {
            return;
        }
        Set<String> versionFields = readTextSet(readPolicy(version.getPolicySnapshot()).path("allowedFields"));
        Set<String> requiredFields = readTextSet(readPolicy(version.getPolicySnapshot()).path("requiredFields"));
        Set<String> grantFields = grant == null
                ? Set.of() : readTextSet(readPolicy(grant.getFieldPolicy()).path("allowedFields"));
        boolean valid = !versionFields.isEmpty()
                && !grantFields.isEmpty()
                && versionFields.containsAll(grantFields)
                && grantFields.containsAll(requiredFields);
        add(checks, "FIELD_POLICY", "申请字段授权", valid, true,
                valid ? "客户端申请字段白名单已按能力版本收窄"
                        : "客户端申请字段授权缺失或超出能力版本，请重新配置授权");
    }

    JsonNode readPolicy(String content) {
        if (StringUtils.isBlank(content)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode policy = objectMapper.readTree(content);
            return policy != null && policy.isObject() ? policy : objectMapper.createObjectNode();
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private void retainExampleFields(JsonNode node, Set<String> allowedFields) {
        if (!(node instanceof ObjectNode object) || allowedFields.isEmpty()) {
            return;
        }
        List<String> fields = new ArrayList<>();
        object.fieldNames().forEachRemaining(fields::add);
        fields.stream()
                .filter(field -> !allowedFields.contains(field))
                .forEach(object::remove);
    }

    private boolean sameFlowBindingSnapshot(JsonNode left, JsonNode right) {
        return sameRequiredPolicyValue(left, right, "bindingId")
                && sameRequiredPolicyValue(left, right, "flowModelKey")
                && sameRequiredPolicyValue(left, right, "publishedObjectVersion");
    }

    private boolean sameRequiredPolicyValue(JsonNode left, JsonNode right, String field) {
        String leftValue = StringUtils.trimToNull(left.path(field).asText());
        String rightValue = StringUtils.trimToNull(right.path(field).asText());
        return leftValue != null && leftValue.equals(rightValue);
    }

    private Set<String> readTextSet(JsonNode values) {
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            String text = value.isTextual() ? StringUtils.trimToNull(value.asText()) : null;
            if (text != null) {
                result.add(text);
            }
        });
        return Set.copyOf(result);
    }

    private void add(List<CapabilityCallGuideCheckVO> checks,
                     String code,
                     String label,
                     boolean passed,
                     boolean blocking,
                     String message) {
        checks.add(new CapabilityCallGuideCheckVO(
                code, label, passed ? "PASSED" : "FAILED", blocking, message));
    }
}
