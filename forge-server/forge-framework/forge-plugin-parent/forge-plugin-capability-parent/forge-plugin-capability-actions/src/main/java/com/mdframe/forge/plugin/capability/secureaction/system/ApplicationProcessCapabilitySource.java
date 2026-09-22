package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationRuntimeService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationVersionService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolve application-owned orchestration versions; never fall back to legacy FLOW bindings. */
@RequiredArgsConstructor
public class ApplicationProcessCapabilitySource {
    private final BusinessApplicationRuntimeService runtime;
    private final BusinessApplicationVersionService applicationVersions;
    private final BusinessObjectMapper objects;
    private final BusinessProcessMapper processes;
    private final BusinessProcessVersionMapper versions;
    private final ObjectMapper mapper;

    public List<Option> options(Long tenant, Long applicationId, Long objectId) {
        Context context = context(tenant, applicationId, objectId);
        List<Option> result = new ArrayList<>();
        for (JsonNode entry : context.snapshot().path("publishedProcessVersions")) {
            if (!matchesSubject(entry.path("businessProcessJson"), context.object())) continue;
            String code = entry.path("processCode").asText();
            try {
                Source source = resolve(tenant, context, entry);
                result.add(new Option(code, source.processName(), source.processVersion(), true, null));
            } catch (BusinessException exception) {
                result.add(new Option(code, code, entry.path("versionNo").asInt(), false, exception.getMessage()));
            }
        }
        return result;
    }

    public Source require(Long tenant, Long applicationId, Long objectId, String processCode) {
        Context context = context(tenant, applicationId, objectId);
        if (StringUtils.isBlank(processCode)) throw new BusinessException("请选择应用业务流程");
        for (JsonNode entry : context.snapshot().path("publishedProcessVersions")) {
            if (processCode.equals(entry.path("processCode").asText())
                    && matchesSubject(entry.path("businessProcessJson"), context.object())) {
                return resolve(tenant, context, entry);
            }
        }
        throw new BusinessException("该页面没有此已发布业务流程，请在应用业务流程中配置并发布应用");
    }

    private Context context(Long tenant, Long applicationId, Long objectId) {
        requireTenant(tenant);
        if (applicationId == null || applicationId <= 0 || objectId == null || objectId <= 0) {
            throw new BusinessException("请选择已发布应用及页面");
        }
        var published = runtime.runtimeById(applicationId); // Includes application/portal access checks.
        BusinessObjectVO object = objects.selectObjectDetail(tenant, objectId);
        if (object == null || !EnableStatus.ENABLED.matches(object.getStatus())) {
            throw new BusinessException("页面业务对象不存在或已停用");
        }
        boolean member = published.getObjects().stream().anyMatch(item ->
                objectId.equals(item.getObjectId()) && Objects.equals(object.getObjectCode(), item.getObjectCode())
                && (StringUtils.isBlank(item.getSuiteCode()) || Objects.equals(object.getSuiteCode(), item.getSuiteCode()))
                && StringUtils.isNotBlank(item.getConfigKey()) && Objects.equals(item.getConfigKey(), object.getConfigKey()));
        if (!member) throw new BusinessException("页面对象不属于当前应用的已发布版本");
        var version = applicationVersions.requireVersion(applicationId, published.getVersionNo());
        return new Context(applicationId, published.getApplication().getApplicationCode(), published.getVersionNo(),
                object, read(version.getSnapshotJson()));
    }

    private Source resolve(Long tenant, Context context, JsonNode entry) {
        Long processId = positiveId(entry.path("processId"));
        var process = processes.selectActiveById(tenant, processId);
        int versionNo = entry.path("versionNo").asInt();
        if (process == null || !EnableStatus.ENABLED.matches(process.getStatus())
                || !context.applicationId().equals(process.getApplicationId())
                || !Objects.equals(process.getProcessCode(), entry.path("processCode").asText())
                || !Objects.equals(process.getPublishedVersion(), versionNo)) {
            throw new BusinessException("业务流程已停用或发布版本已变化，请重新发布应用后注册");
        }
        var version = versions.selectPublishedVersion(tenant, processId, versionNo);
        Long versionId = positiveId(entry.path("processVersionId"));
        if (version == null || !versionId.equals(version.getId())
                || !context.applicationId().equals(version.getApplicationId())
                || StringUtils.isBlank(version.getSchemaHash())
                || !version.getSchemaHash().equals(entry.path("schemaHash").asText())) {
            throw new BusinessException("应用业务流程发布快照不一致，请重新发布应用");
        }
        JsonNode schema = read(version.getSchemaJson());
        if (!matchesSubject(schema, context.object())) throw new BusinessException("业务流程主对象与所选页面不一致");
        List<JsonNode> starts = new ArrayList<>();
        schema.path("nodes").forEach(node -> { if ("START_MANUAL".equals(node.path("type").asText())) starts.add(node); });
        if (starts.size() != 1) throw new BusinessException("该流程不是手动发起流程，事件或定时流程不能通过此入口启动");
        String permission = StringUtils.defaultIfBlank(starts.get(0).path("config").path("permission").asText(),
                "ai:businessProcess:start");
        return new Source(context.applicationId(), context.applicationCode(), context.applicationVersion(),
                context.object().getId(), context.object().getObjectCode(), context.object().getConfigKey(),
                processId, process.getProcessCode(), StringUtils.defaultIfBlank(process.getProcessName(), process.getProcessCode()), versionId, versionNo,
                version.getSchemaHash(), permission);
    }

    private boolean matchesSubject(JsonNode schema, BusinessObjectVO object) {
        return String.valueOf(object.getId()).equals(schema.path("subject").path("objectId").asText())
                && Objects.equals(object.getObjectCode(), schema.path("subject").path("objectCode").asText());
    }

    private JsonNode read(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Invalid snapshot");
            return node;
        }
        catch (Exception exception) { throw new BusinessException("应用业务流程发布快照无法读取"); }
    }

    public static Long positiveId(JsonNode value) {
        try {
            long id = Long.parseLong(value.asText());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new BusinessException("应用、对象或流程版本 ID 无效");
    }

    public static void requireTenant(Long tenant) {
        if (tenant == null || tenant <= 0 || !tenant.equals(SessionHelper.getTenantId())) {
            throw new BusinessException(403, "缺少一致的可信租户上下文");
        }
    }

    private record Context(Long applicationId, String applicationCode, Integer applicationVersion,
                           BusinessObjectVO object, JsonNode snapshot) { }
    public record Option(String processCode, String name, Integer version, boolean available, String unavailableReason) { }
    public record Source(Long applicationId, String applicationCode, Integer applicationVersion,
                         Long objectId, String objectCode, String configKey, Long processId, String processCode,
                         String processName, Long processVersionId, Integer processVersion, String schemaHash,
                         String permission) { }
}
