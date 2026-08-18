package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContext;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContextResolver;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessValidationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 随应用发布生成不可变业务流程版本，并固定对象、Flowable 和受治理能力依赖。
 */
@Service
@RequiredArgsConstructor
public class BusinessProcessPublishService {

    private final ObjectMapper objectMapper;
    private final BusinessProcessMapper processMapper;
    private final BusinessProcessVersionMapper versionMapper;
    private final BusinessObjectDesignVersionMapper objectVersionMapper;
    private final BusinessProcessSchemaValidator schemaValidator;
    private final BusinessProcessValidationContextResolver validationContextResolver;
    private final ObjectProvider<FlowClient> flowClientProvider;

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessPublishResult publishForApplication(
            Long applicationId,
            Integer applicationVersion,
            Collection<Long> processIds) {
        return publishForApplication(applicationId, applicationVersion, processIds, Map.of(), null);
    }

    /**
     * 应用协调发布使用的冻结草稿入口。expectedSchemaHashes 来自运行单候选快照，
     * 避免发布恢复期间读取到后来保存的新草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessPublishResult publishForApplication(
            Long applicationId,
            Integer applicationVersion,
            Collection<Long> processIds,
            Map<Long, String> expectedSchemaHashes,
            Long publishRunId) {
        requireApplicationVersion(applicationId, applicationVersion);
        Long tenantId = resolveTenantId();
        List<Long> selectedIds = normalizeIds(processIds);
        Map<Long, String> expectedHashes = expectedSchemaHashes == null
                ? Map.of() : expectedSchemaHashes;
        List<BusinessProcessSnapshot> snapshots = new ArrayList<>();
        for (Long processId : selectedIds) {
            AiBusinessProcess process = processMapper.selectForPublish(tenantId, applicationId, processId);
            if (process == null) {
                throw new BusinessException("业务流程不属于当前应用、已停用或已失效: " + processId);
            }
            String expectedHash = StringUtils.trimToNull(expectedHashes.get(processId));
            if (publishRunId != null && expectedHash == null) {
                throw new BusinessException(409,
                        "应用发布候选快照缺少业务流程草稿摘要，请重新发起发布: " + process.getProcessCode());
            }
            expectedHash = StringUtils.defaultIfBlank(expectedHash, process.getDraftSchemaHash());
            AiBusinessProcessVersion existing = versionMapper.selectPublishedForApplicationVersion(
                    tenantId, processId, applicationVersion);
            if (existing != null) {
                assertSameImmutableVersion(existing, expectedHash);
                updateProjection(applicationId, processId, existing, resolveUserId());
                snapshots.add(toSnapshot(existing));
                continue;
            }
            if (!StringUtils.equals(expectedHash, process.getDraftSchemaHash())) {
                throw new BusinessException(409, "业务流程草稿已在发布运行单创建后变化: " + process.getProcessCode());
            }
            snapshots.add(publishNewVersion(
                    tenantId, applicationId, applicationVersion, publishRunId, process));
        }
        processMapper.clearPublishedProjectionExcept(
                tenantId, applicationId, selectedIds, resolveUserId());
        return new BusinessProcessPublishResult(snapshots);
    }

    public List<BusinessProcessSnapshot> resolvePublishedSnapshots(
            Long applicationId,
            Collection<Long> processIds) {
        if (applicationId == null || applicationId <= 0) {
            throw new BusinessException("业务应用ID不能为空");
        }
        Set<Long> selectedIds = new LinkedHashSet<>(normalizeIds(processIds));
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        return safeList(versionMapper.selectCurrentPublishedByApplication(
                resolveTenantId(), applicationId)).stream()
                .filter(version -> selectedIds.contains(version.getProcessId()))
                .sorted(Comparator.comparing(AiBusinessProcessVersion::getProcessCode)
                        .thenComparing(AiBusinessProcessVersion::getVersionNo))
                .map(this::toSnapshot)
                .toList();
    }

    /**
     * 回滚只恢复当前流程投影；历史运行实例仍持有原 processVersionId，不做切换。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BusinessProcessSnapshot> restorePublishedProjection(
            Long applicationId,
            Collection<Long> processVersionIds) {
        if (applicationId == null || applicationId <= 0) {
            throw new BusinessException("业务应用ID不能为空");
        }
        Long tenantId = resolveTenantId();
        Long userId = resolveUserId();
        List<BusinessProcessSnapshot> result = new ArrayList<>();
        List<Long> selectedProcessIds = new ArrayList<>();
        for (Long versionId : normalizeIds(processVersionIds)) {
            AiBusinessProcessVersion version = versionMapper.selectPublishedVersionById(tenantId, versionId);
            if (version == null || !applicationId.equals(version.getApplicationId())) {
                throw new BusinessException("历史应用版本依赖的业务流程版本已不存在: " + versionId);
            }
            AiBusinessProcess process = processMapper.selectForProjection(
                    tenantId, applicationId, version.getProcessId());
            if (process == null) {
                throw new BusinessException("历史应用版本依赖的业务流程已删除或失效: " + version.getProcessCode());
            }
            updateProjection(applicationId, version.getProcessId(), version, userId);
            selectedProcessIds.add(version.getProcessId());
            result.add(toSnapshot(version));
        }
        processMapper.clearPublishedProjectionExcept(
                tenantId, applicationId, selectedProcessIds, userId);
        return List.copyOf(result);
    }

    /**
     * 独立发布单条业务流程：同步生成不可变版本并切换该流程的运行投影。
     * 不创建应用发布运行单，也不影响其他流程的已发布投影；
     * applicationVersion 使用负数序列与应用协调发布的正数版本空间隔离，
     * 避免与后续应用发布在 uk_ai_business_process_app_version_active 上互撞。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessSnapshot publishStandalone(Long processId) {
        if (processId == null || processId <= 0) {
            throw new BusinessException("业务流程ID不能为空");
        }
        Long tenantId = resolveTenantId();
        AiBusinessProcess process = processMapper.selectById(processId);
        if (process == null || !tenantId.equals(process.getTenantId()) || process.getApplicationId() == null) {
            throw new BusinessException("业务流程不存在、已删除或不属于当前租户");
        }
        AiBusinessProcess locked = processMapper.selectForPublish(tenantId, process.getApplicationId(), processId);
        if (locked == null) {
            throw new BusinessException("业务流程已停用或已失效，无法发布");
        }
        BusinessProcessSchema schema = normalizeSchema(locked.getDraftSchemaJson());
        String canonicalHash = schemaValidator.schemaHash(schema);
        if (!StringUtils.equals(canonicalHash, locked.getDraftSchemaHash())) {
            throw new BusinessException(409, "业务流程草稿摘要与内容不一致，请先在画布保存草稿后再发布");
        }
        AiBusinessProcessVersion current = locked.getPublishedVersion() == null
                ? null
                : versionMapper.selectPublishedVersion(tenantId, processId, locked.getPublishedVersion());
        if (current != null && StringUtils.equals(current.getSchemaHash(), canonicalHash)) {
            return toSnapshot(current);
        }
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, locked.getApplicationId(), locked.getProcessCode(), schema);
        BusinessProcessValidationVO validation = schemaValidator.validate(schema, context);
        if (!validation.isValid()) {
            String summary = validation.getIssues().stream()
                    .filter(issue -> "ERROR".equals(issue.getLevel()))
                    .limit(3)
                    .map(issue -> issue.getCode() + "：" + issue.getMessage())
                    .collect(Collectors.joining("；"));
            throw new BusinessException(422, "业务流程发布校验未通过: " + summary, validation);
        }
        int nextVersionNo = value(versionMapper.selectMaxVersionNo(tenantId, processId)) + 1;
        return publishNewVersion(tenantId, locked.getApplicationId(), -nextVersionNo, null, locked);
    }

    private BusinessProcessSnapshot publishNewVersion(
            Long tenantId,
            Long applicationId,
            Integer applicationVersion,
            Long publishRunId,
            AiBusinessProcess process) {
        BusinessProcessSchema schema = normalizeSchema(process.getDraftSchemaJson());
        String canonicalJson = schemaValidator.canonicalJson(schema);
        String canonicalHash = schemaValidator.schemaHash(schema);
        if (!StringUtils.equals(canonicalHash, process.getDraftSchemaHash())) {
            throw new BusinessException("业务流程草稿摘要校验失败: " + process.getProcessCode());
        }
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, applicationId, process.getProcessCode(), schema);
        BusinessProcessValidationVO validation = schemaValidator.validate(schema, context);
        if (!validation.isValid()) {
            String summary = validation.getIssues().stream()
                    .filter(issue -> "ERROR".equals(issue.getLevel()))
                    .limit(3)
                    .map(issue -> issue.getCode() + "：" + issue.getMessage())
                    .collect(Collectors.joining("；"));
            throw new BusinessException(422, "业务流程发布校验未通过: " + summary, validation);
        }

        Map<String, Object> dependencies = resolveDependencySnapshot(
                tenantId, applicationId, schema, context);
        Long userId = resolveUserId();
        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setId(IdWorker.getId());
        version.setTenantId(tenantId);
        version.setApplicationId(applicationId);
        version.setProcessId(process.getId());
        version.setProcessCode(process.getProcessCode());
        version.setVersionNo(value(versionMapper.selectMaxVersionNo(tenantId, process.getId())) + 1);
        version.setApplicationVersion(applicationVersion);
        version.setPublishRunId(publishRunId);
        version.setSchemaVersion(schema.getSchemaVersion());
        version.setSchemaJson(canonicalJson);
        version.setSchemaHash(canonicalHash);
        version.setDependencySnapshotJson(writeJson(dependencies));
        version.setPublishTime(LocalDateTime.now());
        version.setPublishedBy(userId);
        version.setStatus(1);
        version.setDelFlag(0L);
        version.setCreateBy(userId);
        version.setCreateDept(resolveActiveOrgId());
        version.setUpdateBy(userId);
        try {
            if (versionMapper.insertImmutable(version) != 1) {
                throw new BusinessException("业务流程版本保存失败: " + process.getProcessCode());
            }
        } catch (DuplicateKeyException exception) {
            AiBusinessProcessVersion existing = versionMapper.selectPublishedForApplicationVersion(
                    tenantId, process.getId(), applicationVersion);
            if (existing == null) {
                throw new BusinessException(409, "业务流程版本并发发布冲突，请稍后重试", exception);
            }
            assertSameImmutableVersion(existing, canonicalHash);
            version = existing;
        }
        updateProjection(applicationId, process.getId(), version, userId);
        return toSnapshot(version);
    }

    private Map<String, Object> resolveDependencySnapshot(
            Long tenantId,
            Long applicationId,
            BusinessProcessSchema schema,
            BusinessProcessValidationContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("objects", resolveObjectDependencies(tenantId, schema, context));
        result.put("flowModels", resolveFlowDependencies(schema));
        result.put("formAssets", referenceList(schema.getDependencies().getFormAssets(), "formKey"));
        result.put("businessActions", referenceList(
                schema.getDependencies().getBusinessActions(), "actionCode"));
        result.put("messageTemplates", referenceList(
                schema.getDependencies().getMessageTemplates(), "templateCode"));
        result.put("capabilities", referenceList(
                schema.getDependencies().getCapabilities(), "capabilityCode"));
        result.put("subProcesses", resolveSubProcessDependencies(
                tenantId, applicationId, schema.getDependencies().getSubProcesses()));
        return result;
    }

    private List<Map<String, Object>> resolveObjectDependencies(
            Long tenantId,
            BusinessProcessSchema schema,
            BusinessProcessValidationContext context) {
        List<String> objectCodes = safeList(schema.getDependencies().getObjects());
        List<Long> objectIds = objectCodes.stream()
                .map(context.getObjectIdsByCode()::get)
                .map(this::longValue)
                .toList();
        Map<Long, AiBusinessObjectDesignVersion> versions = objectIds.isEmpty()
                ? Map.of()
                : safeList(objectVersionMapper.selectLatestPublishedVersions(tenantId, objectIds)).stream()
                .collect(Collectors.toMap(AiBusinessObjectDesignVersion::getObjectId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String objectCode : objectCodes) {
            Long objectId = longValue(context.getObjectIdsByCode().get(objectCode));
            AiBusinessObjectDesignVersion version = versions.get(objectId);
            Long validatedVersionId = longValue(
                    context.getPublishedObjectVersionIdsByCode().get(objectCode));
            if (objectId == null || version == null || version.getId() == null) {
                throw new BusinessException("业务流程依赖对象尚无已发布版本: " + objectCode);
            }
            if (!version.getId().equals(validatedVersionId)) {
                throw new BusinessException(409, "业务流程依赖对象的发布版本已变化，请重新执行应用发布检查: "
                        + objectCode);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("objectId", String.valueOf(objectId));
            item.put("objectCode", objectCode);
            item.put("designVersionId", String.valueOf(version.getId()));
            item.put("versionNo", version.getVersionNo());
            item.put("publishVersion", version.getPublishVersion());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> resolveFlowDependencies(BusinessProcessSchema schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        FlowClient flowClient = flowClientProvider.getIfAvailable();
        if (!schema.getDependencies().getFlowModels().isEmpty() && flowClient == null) {
            throw new BusinessException("Flowable 服务不可用，不能固定审批模型版本");
        }
        for (String modelKey : safeList(schema.getDependencies().getFlowModels())) {
            FlowResult<Map<String, Object>> response;
            try {
                response = flowClient.getModelByKey(modelKey);
            } catch (RuntimeException exception) {
                throw new BusinessException(
                        "无法读取审批模型发布状态，请确认 Flow 服务可用后重试: " + modelKey,
                        exception);
            }
            Map<String, Object> model = response == null ? null : response.getData();
            Integer modelVersion = model == null ? null : integerValue(model.get("version"));
            if (response == null || !response.isSuccess() || model == null
                    || !Integer.valueOf(1).equals(integerValue(model.get("status")))
                    || StringUtils.isBlank(text(model.get("deploymentId")))
                    || StringUtils.isBlank(text(model.get("processDefinitionId")))
                    || modelVersion == null
                    || modelVersion <= 0) {
                throw new BusinessException("审批模型尚未发布或缺少可固定的部署版本: " + modelKey);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelKey", modelKey);
            item.put("modelId", text(model.get("id")));
            item.put("modelVersion", modelVersion);
            item.put("processDefinitionId", text(model.get("processDefinitionId")));
            item.put("deploymentId", text(model.get("deploymentId")));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> resolveSubProcessDependencies(
            Long tenantId,
            Long applicationId,
            List<String> processCodes) {
        if (processCodes == null || processCodes.isEmpty()) {
            return List.of();
        }
        Map<String, AiBusinessProcessVersion> versions = safeList(
                versionMapper.selectCurrentPublishedByApplication(tenantId, applicationId)).stream()
                .collect(Collectors.toMap(AiBusinessProcessVersion::getProcessCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String processCode : processCodes) {
            AiBusinessProcessVersion version = versions.get(processCode);
            if (version == null) {
                throw new BusinessException("子流程尚无当前已发布版本: " + processCode);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processCode", processCode);
            item.put("processId", String.valueOf(version.getProcessId()));
            item.put("processVersionId", String.valueOf(version.getId()));
            item.put("versionNo", version.getVersionNo());
            item.put("schemaHash", version.getSchemaHash());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> referenceList(List<String> values, String key) {
        return safeList(values).stream().map(value -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(key, value);
            return item;
        }).toList();
    }

    private void updateProjection(
            Long applicationId,
            Long processId,
            AiBusinessProcessVersion version,
            Long userId) {
        if (processMapper.updatePublishedProjection(
                resolveTenantId(), applicationId, processId,
                version.getVersionNo(), version.getSchemaHash(), userId) != 1) {
            throw new BusinessException("业务流程发布投影更新失败: " + version.getProcessCode());
        }
    }

    private void assertSameImmutableVersion(AiBusinessProcessVersion existing, String expectedHash) {
        if (!StringUtils.equals(existing.getSchemaHash(), expectedHash)) {
            throw new BusinessException(409, "相同应用版本已固定不同的业务流程草稿: "
                    + existing.getProcessCode());
        }
    }

    private BusinessProcessSnapshot toSnapshot(AiBusinessProcessVersion version) {
        return new BusinessProcessSnapshot(
                String.valueOf(version.getProcessId()),
                String.valueOf(version.getId()),
                version.getProcessCode(),
                version.getVersionNo(),
                version.getApplicationVersion(),
                version.getSchemaVersion(),
                version.getSchemaHash(),
                readMap(version.getSchemaJson(), "业务流程版本协议格式不正确"),
                readMap(version.getDependencySnapshotJson(), "业务流程依赖快照格式不正确"));
    }

    private BusinessProcessSchema normalizeSchema(String schemaJson) {
        try {
            return schemaValidator.normalize(schemaJson);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(422, exception.getMessage(), exception);
        }
    }

    private Map<String, Object> readMap(String json, String message) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception exception) {
            throw new BusinessException(message);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("业务流程依赖快照生成失败");
        }
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private void requireApplicationVersion(Long applicationId, Integer applicationVersion) {
        if (applicationId == null || applicationId <= 0) {
            throw new BusinessException("业务应用ID不能为空");
        }
        if (applicationVersion == null || applicationVersion <= 0) {
            throw new BusinessException("应用发布版本号不能为空");
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long resolveTenantId() {
        try {
            Long tenantId = SessionHelper.getTenantId();
            return tenantId == null ? 1L : tenantId;
        } catch (Exception exception) {
            return 1L;
        }
    }

    private Long resolveUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }

    private Long resolveActiveOrgId() {
        try {
            return SessionHelper.getActiveOrgId();
        } catch (Exception exception) {
            return null;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
