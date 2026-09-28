package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStatus;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStep;
import com.mdframe.forge.plugin.generator.constant.BusinessExtensionStatus;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationPublishRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessExtension;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPublishDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectPublishDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessPublishResult;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessPublishService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationSnapshotService.SnapshotBundle;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAssetSelectionVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationPublishCheckVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationPublishResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationPublishRunVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPermissionSummaryVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 应用级协调发布编排。各业务步骤独立提交，运行单记录部分完成状态并支持恢复。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessApplicationPublishService {

    private final BusinessApplicationReadinessService readinessService;
    private final BusinessApplicationFormDataService formDataService;
    private final BusinessApplicationSnapshotService snapshotService;
    private final BusinessApplicationPublishRunService runService;
    private final BusinessApplicationVersionService versionService;
    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessObjectPublishService objectPublishService;
    private final BusinessObjectDesignerService objectDesignerService;
    private final BusinessObjectDesignVersionService objectVersionService;
    private final BusinessProcessPublishService processPublishService;
    private final BusinessAppService businessAppService;
    private final BusinessApplicationPageMenuPublishService pageMenuPublishService;
    private final BusinessExtensionMapper extensionMapper;
    private final BusinessExtensionExecutionService extensionExecutionService;
    private final LowcodeDdlService ddlService;

    public BusinessApplicationPublishCheckVO check(Long applicationId, BusinessApplicationPublishDTO dto) {
        // 显式「发布检查」仍可物化草稿并同步托管表；真正发布不再走这些重路径。
        prepareApplicationObjectDrafts(applicationId);
        formDataService.synchronizeManagedDatabases(applicationId);
        return ddlService.withStructureCheckCache(() -> readinessService.publishCheck(applicationId, dto));
    }

    public BusinessApplicationPublishResultVO publish(Long applicationId,
                                                      BusinessApplicationPublishDTO dto,
                                                      String idempotencyKey) {
        return ddlService.withStructureCheckCache(() -> doPublish(applicationId, dto, idempotencyKey));
    }

    private BusinessApplicationPublishResultVO doPublish(Long applicationId,
                                                         BusinessApplicationPublishDTO dto,
                                                         String idempotencyKey) {
        AiBusinessApplicationPublishRun existing = runService.findByIdempotencyKey(applicationId, idempotencyKey);
        if (existing != null) {
            return toResult(existing, existingRunMessage(existing));
        }
        // 发布只推进应用状态与不可变版本：不做草稿物化、不做托管表同步、不做对象级全量校验。
        BusinessApplicationReadinessService.ResolvedPublishCheck resolvedCheck
                = readinessService.resolveStatusPublishCheck(applicationId, dto);
        BusinessApplicationPublishCheckVO check = resolvedCheck.check();
        if (!Boolean.TRUE.equals(check.getPublishable())) {
            throw new BusinessException(blockedMessage(check));
        }
        SnapshotBundle candidate = snapshotService.prepare(
                applicationId, resolvedCheck.application(), resolvedCheck.selection(),
                resolvedCheck.permissionSummaries(), resolvedCheck.bindings());
        AiBusinessApplicationPublishRun run = runService.reserve(applicationId, idempotencyKey,
                "PUBLISH", null, candidate, check.getSelection());
        if (BusinessApplicationPublishStatus.SUCCESS.matches(run.getRunStatus())
                || BusinessApplicationPublishStatus.PARTIAL.matches(run.getRunStatus())
                || BusinessApplicationPublishStatus.FAILED.matches(run.getRunStatus())
                || BusinessApplicationPublishStatus.RUNNING.matches(run.getRunStatus())) {
            return toResult(run, existingRunMessage(run));
        }
        if (!runService.tryClaimCreated(applicationId, run.getId())) {
            return toResult(runService.requireRun(applicationId, run.getId()), "相同幂等请求已由另一执行器处理");
        }
        run.setRunStatus(BusinessApplicationPublishStatus.RUNNING.getCode());
        return resume(run,
                dto == null ? new BusinessApplicationPublishDTO() : dto, resolvedCheck, false);
    }

    public BusinessApplicationPublishResultVO resume(AiBusinessApplicationPublishRun run,
                                                     BusinessApplicationPublishDTO dto) {
        return ddlService.withStructureCheckCache(() -> resume(run, dto, null, true));
    }

    private BusinessApplicationPublishResultVO resume(AiBusinessApplicationPublishRun run,
                                                       BusinessApplicationPublishDTO dto,
                                                       BusinessApplicationReadinessService.ResolvedPublishCheck initialCheck,
                                                       boolean forcePrecheck) {
        String step = run.getCurrentStep();
        try {
            BusinessApplicationAssetSelectionVO selection = runService.readSelection(run);
            BusinessApplicationPublishDTO effectiveDto = dtoFromSelection(selection, dto);
            Map<Long, BusinessPermissionSummaryVO> permissionSummaries = Map.of();
            Map<Long, BusinessObjectDesignerService.DesignerContext> objectContexts = Map.of();
            if (forcePrecheck || !runService.isStepComplete(run, BusinessApplicationPublishStep.PRECHECK)) {
                step = BusinessApplicationPublishStep.PRECHECK;
                run = runService.markStepRunning(run, step);
                BusinessApplicationReadinessService.ResolvedPublishCheck resolvedCheck = initialCheck == null
                        ? readinessService.resolveStatusPublishCheck(run.getApplicationId(), effectiveDto)
                        : initialCheck;
                BusinessApplicationPublishCheckVO check = resolvedCheck.check();
                if (!Boolean.TRUE.equals(check.getPublishable())) {
                    return fail(run, step, "PUBLISH_PRECHECK_BLOCKED", blockedMessage(check));
                }
                permissionSummaries = resolvedCheck.permissionSummaries().stream()
                        .collect(Collectors.toMap(BusinessPermissionSummaryVO::getObjectId, Function.identity()));
                objectContexts = resolvedCheck.objectContexts();
                run = runService.markStepSuccess(run, step,
                        check.getWarningCount() + " 项提醒，不阻断发布");
            }
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.SNAPSHOT)) {
                step = BusinessApplicationPublishStep.SNAPSHOT;
                run = runService.markStepRunning(run, step);
                run = runService.markStepSuccess(run, step, "候选快照摘要 " + shortHash(run.getSnapshotHash()));
            }

            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.PROCESSES)) {
                step = BusinessApplicationPublishStep.PROCESSES;
                run = runService.markStepRunning(run, step);
                BusinessProcessPublishResult processResult = processPublishService.publishForApplication(
                        run.getApplicationId(),
                        run.getTargetVersionNo(),
                        selection.getProcessIds(),
                        readProcessDraftHashes(run.getSnapshotJson()),
                        run.getId());
                SnapshotBundle processSnapshot = snapshotService.finalizeProcesses(
                        run.getSnapshotJson(), processResult.snapshots());
                run = runService.markStepSuccess(run, step,
                        "已固定 " + processResult.snapshots().size() + " 个业务流程版本",
                        processSnapshot);
            }

            Map<Long, Long> objectVersions = readPublishedObjectVersions(run.getSnapshotJson());
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.OBJECTS)) {
                step = BusinessApplicationPublishStep.OBJECTS;
                run = runService.markStepRunning(run, step);
                PublishObjectsResult publishResult = publishObjects(
                        run, selection, effectiveDto, objectVersions, permissionSummaries, objectContexts);
                run = publishResult.run();
                objectVersions = publishResult.objectVersions();
                SnapshotBundle objectSnapshot = snapshotService.finalizePublished(
                        run.getSnapshotJson(), objectVersions, selection, run.getTargetVersionNo(), "PUBLISH");
                run = runService.markStepSuccess(run, step,
                        "已处理 " + objectVersions.size() + " 个业务对象", objectSnapshot);
            } else if (objectVersions.isEmpty() && !selection.getObjectIds().isEmpty()) {
                // 崩溃恢复：OBJECTS 已成功但快照未写入版本钉时，COMMIT 前按轻量查询补齐。
                objectVersions = objectVersionService.latestPublishedVersionIds(selection.getObjectIds());
            }
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.ENTRIES)) {
                step = BusinessApplicationPublishStep.ENTRIES;
                run = runService.markStepRunning(run, step);
                List<Long> entries = businessAppService.publishEntries(run.getApplicationId(), selection.getEntryIds());
                run = runService.markStepSuccess(run, step, "已切换 " + entries.size() + " 个页面入口");
            }
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.PAGE_MENUS)) {
                step = BusinessApplicationPublishStep.PAGE_MENUS;
                run = runService.markStepRunning(run, step);
                Map<String, Object> snapshot = snapshotService.parse(run.getSnapshotJson());
                int count = pageMenuPublishService.sync(snapshot).size();
                run = runService.markStepSuccess(run, step, "已同步 " + count + " 个应用页面菜单",
                        snapshotService.bundle(snapshot));
            }
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.EXTENSIONS)) {
                step = BusinessApplicationPublishStep.EXTENSIONS;
                run = runService.markStepRunning(run, step);
                int enabledCount = enableExtensions(run.getApplicationId(), selection.getExtensionIds());
                run = runService.markStepSuccess(run, step, "已确认 " + enabledCount + " 个扩展运行版本");
            }
            if (!runService.isStepComplete(run, BusinessApplicationPublishStep.COMMIT)) {
                step = BusinessApplicationPublishStep.COMMIT;
                run = runService.markStepRunning(run, step);
                SnapshotBundle finalSnapshot = snapshotService.finalizePublished(
                        run.getSnapshotJson(), objectVersions, selection, run.getTargetVersionNo(), "PUBLISH");
                AiBusinessApplicationVersion version = versionService.commitImmutable(
                        run.getApplicationId(), run.getTargetVersionNo(), finalSnapshot,
                        BusinessApplicationPublishStatus.PUBLISHED.getCode(), null,
                        StringUtils.defaultIfBlank(dto.getRemark(), "应用协调发布成功"));
                run = runService.markSuccess(run, version.getId(), finalSnapshot);
            }
            return toResult(run, "应用 v" + run.getTargetVersionNo() + " 发布成功");
        } catch (BusinessException e) {
            return fail(run, step, "PUBLISH_STEP_FAILED", safeMessage(e));
        } catch (Exception e) {
            String diagnosticRef = diagnosticRef();
            log.error("[业务应用发布] unexpected step failure: applicationId={}, runId={}, step={}, "
                            + "errorType={}, diagnosticRef={}",
                    run.getApplicationId(), run.getId(), step, e.getClass().getSimpleName(), diagnosticRef, e);
            return fail(run, step, "PUBLISH_INTERNAL_ERROR", safeInternalMessage(step, diagnosticRef));
        }
    }

    private PublishObjectsResult publishObjects(AiBusinessApplicationPublishRun run,
                                                BusinessApplicationAssetSelectionVO selection,
                                                BusinessApplicationPublishDTO dto,
                                                Map<Long, Long> completedVersions,
                                                Map<Long, BusinessPermissionSummaryVO> permissionSummaries,
                                                Map<Long, BusinessObjectDesignerService.DesignerContext> objectContexts) {
        // 候选快照已含对象清单与 designStatus，避免再拉带 model_schema 的 selectByApplicationId。
        Map<Long, BusinessApplicationObjectVO> objects = readObjectsFromSnapshot(run.getSnapshotJson());
        Map<Long, Long> latestPublishedVersions
                = objectVersionService.latestPublishedVersionIds(selection.getObjectIds());
        Map<Long, Long> result = new LinkedHashMap<>(completedVersions);
        for (Long objectId : selection.getObjectIds()) {
            if (result.containsKey(objectId)) {
                continue;
            }
            BusinessApplicationObjectVO object = objects.get(objectId);
            if (object == null) {
                throw new BusinessException("发布对象不属于当前应用: " + objectId);
            }
            Long existingVersion = latestPublishedVersions.get(objectId);
            // 只有无未发布改动的对象才钉住旧版本；有改动（CHANGED 等）必须真正重发，
            // 否则子表、字段等设计改动被标成已发布却从未上线，门户一直读旧快照。
            if (existingVersion != null && BusinessObjectDesignStatus.PUBLISHED.matches(object.getDesignStatus())) {
                result.put(objectId, existingVersion);
                continue;
            }
            BusinessObjectPublishDTO objectDto = new BusinessObjectPublishDTO();
            // 已发布对象重发时，草稿新增字段的列从未建过（真正发布不再走发布检查的建表），
            // 有权限就走对象发布的受控同步；改类型/长度等不安全 DDL 仍由发布检查阻断、人工处理。
            objectDto.setSyncTable(existingVersion != null && hasDdlPermission());
            objectDto.setSyncMenu(false);
            objectDto.setForce(false);
            objectDto.setRemark("由应用协调发布: " + StringUtils.defaultString(dto.getRemark()));
            result.put(objectId, objectPublishService.publish(
                    objectId, objectDto, permissionSummaries.get(objectId), objectContexts.get(objectId)));
            BusinessApplicationObjectVO published = objects.get(objectId);
            if (published != null) {
                published.setDesignStatus(BusinessObjectDesignStatus.PUBLISHED.getCode());
            }
        }
        verifyPublishedObjects(objects, selection.getObjectIds(), result);
        return new PublishObjectsResult(run, result);
    }

    private boolean hasDdlPermission() {
        try {
            return SessionHelper.hasPermission("ai:lowcode:deploy-ddl");
        } catch (Exception e) {
            return false;
        }
    }

    private Map<Long, BusinessApplicationObjectVO> readObjectsFromSnapshot(String snapshotJson) {
        Map<Long, BusinessApplicationObjectVO> result = new LinkedHashMap<>();
        Object value = snapshotService.parse(snapshotJson).get("objects");
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Long objectId = longValue(map.get("objectId"));
            if (objectId == null) {
                continue;
            }
            BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
            object.setObjectId(objectId);
            object.setObjectCode(map.get("objectCode") == null ? null : String.valueOf(map.get("objectCode")));
            object.setObjectName(map.get("objectName") == null ? null : String.valueOf(map.get("objectName")));
            object.setObjectRole(map.get("objectRole") == null ? null : String.valueOf(map.get("objectRole")));
            object.setDesignStatus(map.get("designStatus") == null ? null : String.valueOf(map.get("designStatus")));
            object.setConfigKey(map.get("configKey") == null ? null : String.valueOf(map.get("configKey")));
            result.put(objectId, object);
        }
        return result;
    }

    private void prepareApplicationObjectDrafts(Long applicationId) {
        // 只刷新仍在草稿/已改动的对象；PRIMARY 额外落库关系聚合图。
        // 已 PUBLISHED 的对象正式发布步骤会跳过，门禁阶段不再 compile/写库。
        applicationObjectService.list(applicationId).stream()
                .filter(object -> object.getObjectId() != null)
                .filter(this::needsRuntimeDraftPrepare)
                .forEach(object -> objectDesignerService.prepareRuntimeDraft(
                        object.getObjectId(),
                        BusinessApplicationObjectRole.PRIMARY.equalsIgnoreCase(object.getObjectRole())));
    }

    private boolean needsRuntimeDraftPrepare(BusinessApplicationObjectVO object) {
        if (BusinessObjectDesignStatus.PUBLISHED.matches(object.getDesignStatus())) {
            return false;
        }
        if (BusinessApplicationObjectRole.PRIMARY.equalsIgnoreCase(object.getObjectRole())) {
            return true;
        }
        return BusinessObjectDesignStatus.DRAFT.matches(object.getDesignStatus())
                || BusinessObjectDesignStatus.CHANGED.matches(object.getDesignStatus());
    }

    private void verifyPublishedObjects(Map<Long, BusinessApplicationObjectVO> currentObjects,
                                        List<Long> selectedObjectIds,
                                        Map<Long, Long> objectVersions) {
        List<String> incomplete = selectedObjectIds.stream()
                .filter(objectId -> {
                    BusinessApplicationObjectVO object = currentObjects.get(objectId);
                    return object == null
                            || !BusinessObjectDesignStatus.PUBLISHED.matches(object.getDesignStatus())
                            || objectVersions.get(objectId) == null;
                })
                .map(objectId -> {
                    BusinessApplicationObjectVO object = currentObjects.get(objectId);
                    return object == null
                            ? String.valueOf(objectId)
                            : StringUtils.defaultIfBlank(object.getObjectName(), object.getObjectCode());
                })
                .toList();
        if (!incomplete.isEmpty()) {
            throw new BusinessException("业务对象发布状态未完成: " + String.join("、", incomplete));
        }
    }

    private int enableExtensions(Long applicationId, List<Long> extensionIds) {
        Map<Long, AiBusinessExtension> extensions = extensionMapper
                .selectByApplicationId(resolveTenantId(), applicationId).stream()
                .collect(Collectors.toMap(AiBusinessExtension::getId, Function.identity()));
        int count = 0;
        for (Long extensionId : extensionIds) {
            AiBusinessExtension extension = extensions.get(extensionId);
            if (extension == null) {
                throw new BusinessException("发布扩展不属于当前应用: " + extensionId);
            }
            if (BusinessExtensionStatus.TESTED.matches(extension.getStatus())) {
                extensionExecutionService.updateStatus(extensionId, BusinessExtensionStatus.ENABLED.getCode());
            } else if (BusinessExtensionStatus.DISABLED.matches(extension.getStatus())) {
                count++;
                continue;
            } else if (!BusinessExtensionStatus.ENABLED.matches(extension.getStatus())
                    || !java.util.Objects.equals(extension.getDraftVersion(), extension.getEnabledVersion())) {
                throw new BusinessException("扩展未通过测试或运行版本落后: " + extension.getExtensionName());
            }
            count++;
        }
        return count;
    }

    private Map<Long, Long> readPublishedObjectVersions(String snapshotJson) {
        Map<Long, Long> result = new LinkedHashMap<>();
        Object value = snapshotService.parse(snapshotJson).get("publishedObjectVersions");
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Long objectId = longValue(map.get("objectId"));
            Long versionId = longValue(map.get("designVersionId"));
            if (objectId != null && versionId != null) {
                result.put(objectId, versionId);
            }
        }
        return result;
    }

    private BusinessApplicationPublishDTO dtoFromSelection(BusinessApplicationAssetSelectionVO selection,
                                                           BusinessApplicationPublishDTO source) {
        BusinessApplicationPublishDTO dto = new BusinessApplicationPublishDTO();
        dto.setSelectedObjectIds(selection.getObjectIds());
        dto.setSelectedEntryIds(selection.getEntryIds());
        dto.setSelectedExtensionIds(selection.getExtensionIds());
        dto.setSelectedProcessIds(selection.getProcessIds());
        dto.setIncludeAutomation(selection.getIncludeAutomation());
        dto.setForceWarnings(source == null ? false : source.getForceWarnings());
        dto.setRemark(source == null ? null : source.getRemark());
        return dto;
    }

    private Map<Long, String> readProcessDraftHashes(String snapshotJson) {
        Map<Long, String> result = new LinkedHashMap<>();
        Object value = snapshotService.parse(snapshotJson).get("processes");
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Long processId = longValue(map.get("id"));
            String schemaHash = map.get("draftSchemaHash") == null
                    ? null : String.valueOf(map.get("draftSchemaHash"));
            if (processId != null && StringUtils.isNotBlank(schemaHash)) {
                result.put(processId, schemaHash);
            }
        }
        return result;
    }

    private BusinessApplicationPublishResultVO fail(AiBusinessApplicationPublishRun run, String step,
                                                    String code, String message) {
        AiBusinessApplicationPublishRun failed = runService.markFailed(run,
                StringUtils.defaultIfBlank(step, BusinessApplicationPublishStep.PRECHECK), code, message);
        return toResult(failed, message);
    }

    private BusinessApplicationPublishResultVO toResult(AiBusinessApplicationPublishRun run, String message) {
        BusinessApplicationPublishRunVO detail = runService.toVO(run);
        BusinessApplicationPublishResultVO result = new BusinessApplicationPublishResultVO();
        result.setRunId(run.getId());
        result.setApplicationId(run.getApplicationId());
        result.setOperationType(run.getOperationType());
        result.setRunStatus(run.getRunStatus());
        result.setTargetVersionNo(run.getTargetVersionNo());
        result.setResultVersionId(run.getResultVersionId());
        result.setRecoverable(Set.of(BusinessApplicationPublishStatus.PARTIAL.getCode(),
                BusinessApplicationPublishStatus.FAILED.getCode()).contains(run.getRunStatus()));
        result.setCurrentStep(run.getCurrentStep());
        result.setErrorCode(run.getErrorCode());
        result.setMessage(message);
        result.setSteps(detail.getSteps());
        return result;
    }

    private String blockedMessage(BusinessApplicationPublishCheckVO check) {
        String issues = check.getIssues().stream().filter(item -> "BLOCK".equals(item.getLevel()))
                .limit(3).map(item -> item.getTitle() + "：" + item.getMessage())
                .reduce((left, right) -> left + "；" + right).orElse("发布检查未通过");
        return "应用存在 " + check.getBlockingCount() + " 个发布阻断项：" + issues;
    }

    private String existingRunMessage(AiBusinessApplicationPublishRun run) {
        BusinessApplicationPublishStatus status = BusinessApplicationPublishStatus.of(run.getRunStatus());
        if (status == null) {
            return "相同幂等请求正在执行";
        }
        return switch (status) {
            case SUCCESS -> "相同幂等请求已成功完成";
            case PARTIAL -> "相同幂等请求部分完成，请执行恢复";
            case FAILED -> "相同幂等请求已失败，请修复后执行恢复";
            default -> "相同幂等请求正在执行";
        };
    }

    private String safeMessage(BusinessException exception) {
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(exception.getMessage(), "发布步骤失败"), 500);
    }

    private String safeInternalMessage(String step, String diagnosticRef) {
        String stepName = switch (StringUtils.defaultString(step)) {
            case BusinessApplicationPublishStep.PROCESSES -> "发布业务流程";
            case BusinessApplicationPublishStep.OBJECTS -> "发布业务对象";
            case BusinessApplicationPublishStep.ENTRIES -> "切换页面入口";
            case BusinessApplicationPublishStep.PAGE_MENUS -> "同步应用页面菜单";
            case BusinessApplicationPublishStep.EXTENSIONS -> "启用业务扩展";
            case BusinessApplicationPublishStep.COMMIT -> "提交应用版本";
            case BusinessApplicationPublishStep.SNAPSHOT -> "准备快照";
            default -> "发布预检查";
        };
        String hint = BusinessApplicationPublishStep.PROCESSES.equals(step)
                ? "请确认流程模型已发布、Flow 服务可用且流程依赖对象已有发布版本"
                : "请打开发布运行记录检查该步骤的依赖配置后再恢复";
        return String.format("发布步骤「%s」执行失败。%s；诊断编号：%s", stepName, hint, diagnosticRef);
    }

    private String diagnosticRef() {
        return "PUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String shortHash(String value) {
        return StringUtils.length(value) <= 12 ? value : value.substring(0, 12);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveTenantId() {
        try {
            Long tenantId = SessionHelper.getTenantId();
            return tenantId == null ? 1L : tenantId;
        } catch (Exception e) {
            return 1L;
        }
    }

    private record PublishObjectsResult(AiBusinessApplicationPublishRun run,
                                        Map<Long, Long> objectVersions) {
    }
}
