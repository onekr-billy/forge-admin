package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按当前对象/应用投影已发布或设计预览中的手动开始按钮。
 */
@Service
@RequiredArgsConstructor
public class BusinessProcessRuntimeActionProjectionService {

    private final BusinessProcessVersionMapper versionMapper;
    private final BusinessProcessMapper processMapper;
    private final BusinessApplicationMapper applicationMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessProcessPublishService publishService;
    private final BusinessProcessRuntimeActionCompiler compiler;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> compileForObject(String objectCode, Long applicationId) {
        return compileForObject(objectCode, applicationId, false);
    }

    public List<Map<String, Object>> compileForObject(String objectCode, Long applicationId, boolean includeDraft) {
        return compileForCodes(List.of(objectCode), null, applicationId, includeDraft);
    }

    public List<Map<String, Object>> compileForRender(
            String configKey,
            String objectCode,
            Object modelSchema,
            Long applicationId,
            boolean includeDraft) {
        return compileForCodes(resolveObjectCodes(configKey, objectCode, modelSchema), configKey, applicationId, includeDraft);
    }

    private List<Map<String, Object>> compileForCodes(
            List<String> objectCodes,
            String configKey,
            Long applicationId,
            boolean includeDraft) {
        Long tenantId = requireTenantId();
        Set<String> codes = new LinkedHashSet<>();
        for (String code : objectCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (StringUtils.isNotBlank(configKey)) {
            AiBusinessObject byConfigKey = businessObjectMapper.selectByConfigKey(tenantId, configKey.trim());
            if (byConfigKey != null && StringUtils.isNotBlank(byConfigKey.getObjectCode())) {
                codes.add(byConfigKey.getObjectCode().trim());
            }
        }
        for (String code : List.copyOf(codes)) {
            AiBusinessObject byCode = businessObjectMapper.selectFirstByObjectCode(tenantId, code);
            if (byCode != null && StringUtils.isNotBlank(byCode.getObjectCode())) {
                codes.add(byCode.getObjectCode().trim());
            }
        }
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (String code : codes) {
            for (Map<String, Object> action : compilePublished(tenantId, code, applicationId)) {
                unique.put(String.valueOf(action.get("key")), action);
            }
            if (includeDraft) {
                for (Map<String, Object> action : compileDrafts(tenantId, code)) {
                    unique.put(String.valueOf(action.get("key")), action);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private List<Map<String, Object>> compilePublished(Long tenantId, String objectCode, Long applicationId) {
        List<AiBusinessProcessVersion> versions = applicationId != null && applicationId > 0
                ? safeList(versionMapper.selectCurrentPublishedByApplication(tenantId, applicationId))
                : safeList(versionMapper.selectCurrentPublishedBySubjectObjectCode(tenantId, objectCode));
        List<Map<String, Object>> actions = new ArrayList<>();
        Map<Long, String> applicationCodes = new LinkedHashMap<>();
        for (AiBusinessProcessVersion version : versions) {
            if (version == null || !matchesObject(version, objectCode)) {
                continue;
            }
            BusinessProcessSnapshot snapshot = publishService.toRuntimeSnapshot(version);
            String applicationCode = applicationCodes.computeIfAbsent(
                    version.getApplicationId(), id -> resolveApplicationCode(tenantId, id));
            if (StringUtils.isBlank(applicationCode)) {
                continue;
            }
            AiBusinessProcess process = processMapper.selectActiveById(tenantId, version.getProcessId());
            String processName = process == null ? version.getProcessCode() : process.getProcessName();
            actions.addAll(compiler.compileActions(snapshot, applicationCode, processName));
        }
        return actions;
    }

    private List<Map<String, Object>> compileDrafts(Long tenantId, String objectCode) {
        List<Map<String, Object>> actions = new ArrayList<>();
        for (AiBusinessProcess process : safeList(processMapper.selectActiveDraftsBySubjectObjectCode(tenantId, objectCode))) {
            if (process == null || !EnableStatus.ENABLED.matches(process.getStatus()) || StringUtils.isBlank(process.getDraftSchemaJson())) {
                continue;
            }
            String applicationCode = resolveApplicationCode(tenantId, process.getApplicationId());
            if (StringUtils.isBlank(applicationCode)) {
                continue;
            }
            BusinessProcessSchema schema = readSchema(process.getDraftSchemaJson());
            if (schema == null) {
                continue;
            }
            actions.addAll(compiler.compileSchema(
                    schema, applicationCode, process.getProcessCode(), process.getProcessName()));
        }
        return actions;
    }

    private boolean matchesObject(AiBusinessProcessVersion version, String objectCode) {
        if (version == null || StringUtils.isBlank(objectCode)) {
            return false;
        }
        BusinessProcessSnapshot snapshot = publishService.toRuntimeSnapshot(version);
        if (snapshot == null || snapshot.businessProcessJson() == null) {
            return StringUtils.equalsIgnoreCase(objectCode, version.getProcessCode());
        }
        Object subject = snapshot.businessProcessJson().get("subject");
        if (!(subject instanceof Map<?, ?> map)) {
            return true;
        }
        Object code = map.get("objectCode");
        return code == null || objectCode.equalsIgnoreCase(String.valueOf(code));
    }

    private List<String> resolveObjectCodes(String configKey, String objectCode, Object modelSchema) {
        List<String> codes = new ArrayList<>();
        addCode(codes, objectCode);
        addCode(codes, configKey);
        Map<String, Object> model = readMap(modelSchema);
        Object object = model.get("object");
        if (object instanceof Map<?, ?> map) {
            addCode(codes, map.get("code"));
            addCode(codes, map.get("objectCode"));
        }
        addCode(codes, model.get("objectCode"));
        addCode(codes, model.get("code"));
        return codes;
    }

    private void addCode(List<String> codes, Object value) {
        String text = value == null ? "" : StringUtils.trimToEmpty(String.valueOf(value));
        if (StringUtils.isNotBlank(text) && codes.stream().noneMatch(item -> item.equalsIgnoreCase(text))) {
            codes.add(text);
        }
    }

    private BusinessProcessSchema readSchema(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
            return compiler.schemaFromJson(map);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String resolveApplicationCode(Long tenantId, Long applicationId) {
        if (applicationId == null || applicationId <= 0) {
            return "";
        }
        AiBusinessApplication application = applicationMapper.selectEntityById(tenantId, applicationId);
        return application == null ? "" : StringUtils.trimToEmpty(application.getApplicationCode());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Long requireTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception ignored) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }
}
