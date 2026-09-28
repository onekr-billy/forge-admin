package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.constant.BusinessExtensionStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessExtension;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPublishDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAssetSelectionVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 解析用户选择并自动补齐主对象、入口对象和扩展对象依赖。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationAssetSelectionService {

    private static final Set<String> DEFAULT_PUBLISHABLE_EXTENSION_STATUSES = Set.of(
            BusinessExtensionStatus.TESTED.getCode(),
            BusinessExtensionStatus.ENABLED.getCode(),
            BusinessExtensionStatus.DISABLED.getCode()
    );

    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessAppMapper businessAppMapper;
    private final BusinessExtensionMapper extensionMapper;
    private final BusinessProcessMapper processMapper;

    ResolvedSelection resolveContext(Long applicationId, BusinessApplicationPublishDTO dto) {
        List<BusinessApplicationObjectVO> objects = applicationObjectService.list(applicationId);
        List<AiBusinessApp> entries = businessAppMapper.selectByApplicationId(resolveTenantId(), applicationId);
        List<AiBusinessExtension> extensions = extensionMapper.selectByApplicationId(resolveTenantId(), applicationId);
        List<AiBusinessProcess> processes = processMapper.selectByApplicationId(resolveTenantId(), applicationId);
        Map<Long, BusinessApplicationObjectVO> objectMap = objects.stream()
                .collect(Collectors.toMap(BusinessApplicationObjectVO::getObjectId, Function.identity()));
        Map<Long, AiBusinessApp> entryMap = entries.stream()
                .collect(Collectors.toMap(AiBusinessApp::getId, Function.identity()));
        Map<Long, AiBusinessExtension> extensionMap = extensions.stream()
                .collect(Collectors.toMap(AiBusinessExtension::getId, Function.identity()));
        Map<Long, AiBusinessProcess> processMap = processes.stream()
                .collect(Collectors.toMap(AiBusinessProcess::getId, Function.identity()));

        BusinessApplicationAssetSelectionVO selection = new BusinessApplicationAssetSelectionVO();
        Set<Long> objectIds = initialSelection(dto == null ? null : dto.getSelectedObjectIds(), objectMap.keySet());
        List<Long> requestedEntryIds = dto == null ? null : dto.getSelectedEntryIds();
        boolean explicitEmptyEntrySelection = requestedEntryIds != null && requestedEntryIds.isEmpty();
        Set<Long> entryIds = requestedEntryIds == null
                ? defaultPublishableEntryIds(entries)
                : requestedEntryIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        validateOwned("访问入口", entryIds, entryMap.keySet());
        if (requestedEntryIds != null) {
            long skippedEntryCount = entryIds.stream()
                    .map(entryMap::get)
                    .filter(entry -> !isPublishableEntry(entry))
                    .count();
            entryIds.removeIf(entryId -> !isPublishableEntry(entryMap.get(entryId)));
            if (skippedEntryCount > 0L) {
                selection.getDependencyMessages().add(skippedEntryCount
                        + " 个未启用或未完成配置的访问入口已自动跳过");
            }
        }
        List<Long> requestedExtensionIds = dto == null ? null : dto.getSelectedExtensionIds();
        Set<Long> extensionIds = requestedExtensionIds == null || requestedExtensionIds.isEmpty()
                ? defaultPublishableExtensionIds(extensions)
                : requestedExtensionIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean includeAutomation = dto == null || !Boolean.FALSE.equals(dto.getIncludeAutomation());
        List<Long> requestedProcessIds = dto == null ? null : dto.getSelectedProcessIds();
        Set<Long> processIds = !includeAutomation
                ? new LinkedHashSet<>()
                : requestedProcessIds == null || requestedProcessIds.isEmpty()
                ? defaultPublishableProcessIds(processes)
                : requestedProcessIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        validateOwned("业务对象", objectIds, objectMap.keySet());
        validateOwned("业务扩展", extensionIds, extensionMap.keySet());
        validateOwned("业务流程", processIds, processMap.keySet());
        long skippedDraftCount = extensions.stream()
                .filter(extension -> BusinessExtensionStatus.DRAFT.matches(extension.getStatus()))
                .filter(extension -> !extensionIds.contains(extension.getId()))
                .count();
        if (skippedDraftCount > 0L) {
            selection.getDependencyMessages().add(skippedDraftCount
                    + " 个未测试扩展仍保留为草稿，已自动跳过本次发布");
        }

        objects.stream()
                .filter(item -> BusinessApplicationObjectRole.PRIMARY.equals(item.getObjectRole()))
                .map(BusinessApplicationObjectVO::getObjectId)
                .findFirst()
                .ifPresent(primaryId -> autoIncludeObject(primaryId, objectIds, selection, "主对象是应用发布的必需依赖"));
        for (Long entryId : entryIds) {
            AiBusinessApp entry = entryMap.get(entryId);
            if (entry != null && entry.getObjectCode() != null) {
                objects.stream()
                        .filter(item -> entry.getObjectCode().equals(item.getObjectCode()))
                        .map(BusinessApplicationObjectVO::getObjectId)
                        .findFirst()
                        .ifPresent(objectId -> autoIncludeObject(objectId, objectIds, selection,
                                "访问入口“" + entry.getAppName() + "”依赖对应业务对象"));
            }
        }
        for (Long extensionId : extensionIds) {
            AiBusinessExtension extension = extensionMap.get(extensionId);
            if (extension != null && extension.getObjectId() != null) {
                autoIncludeObject(extension.getObjectId(), objectIds, selection,
                        "扩展“" + extension.getExtensionName() + "”依赖对应业务对象");
            }
            if (extension != null && extension.getEntryId() != null && !entryIds.contains(extension.getEntryId())) {
                AiBusinessApp entry = entryMap.get(extension.getEntryId());
                if (entry == null) {
                    throw new BusinessException("业务扩展关联了不属于当前应用的访问入口");
                }
                if (!explicitEmptyEntrySelection && isPublishableEntry(entry)) {
                    entryIds.add(extension.getEntryId());
                    selection.getDependencyMessages().add("扩展“" + extension.getExtensionName() + "”自动补齐页面入口");
                } else {
                    selection.getDependencyMessages().add("扩展“" + extension.getExtensionName()
                            + "”关联的访问入口未启用或未完成配置，已跳过入口发布");
                }
            }
        }

        selection.setObjectIds(List.copyOf(objectIds));
        selection.setEntryIds(List.copyOf(entryIds));
        selection.setExtensionIds(List.copyOf(extensionIds));
        selection.setProcessIds(List.copyOf(processIds));
        selection.setIncludeAutomation(includeAutomation);
        return new ResolvedSelection(selection, List.copyOf(objects), List.copyOf(entries),
                List.copyOf(extensions), List.copyOf(processes));
    }

    private Set<Long> initialSelection(List<Long> requested, Set<Long> allIds) {
        if (requested == null || requested.isEmpty()) {
            return new LinkedHashSet<>(allIds);
        }
        return requested.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<Long> defaultPublishableExtensionIds(List<AiBusinessExtension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return extensions.stream()
                .filter(extension -> DEFAULT_PUBLISHABLE_EXTENSION_STATUSES.contains(extension.getStatus()))
                .map(AiBusinessExtension::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<Long> defaultPublishableEntryIds(List<AiBusinessApp> entries) {
        if (entries == null || entries.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return entries.stream()
                .filter(BusinessApplicationAssetSelectionService::isPublishableEntry)
                .map(AiBusinessApp::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static boolean isPublishableEntry(AiBusinessApp entry) {
        return entry != null
                && EnableStatus.ENABLED.matches(entry.getStatus())
                && (!"RUNTIME".equalsIgnoreCase(entry.getEntryMode())
                || org.apache.commons.lang3.StringUtils.isNotBlank(entry.getConfigKey()));
    }

    static Set<Long> defaultPublishableProcessIds(List<AiBusinessProcess> processes) {
        if (processes == null || processes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return processes.stream()
                .filter(process -> EnableStatus.ENABLED.matches(process.getStatus()))
                .map(AiBusinessProcess::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateOwned(String assetName, Set<Long> selected, Set<Long> owned) {
        if (!owned.containsAll(selected)) {
            throw new BusinessException("发布选择中包含不属于当前应用的" + assetName);
        }
    }

    private void autoIncludeObject(Long objectId, Set<Long> objectIds,
                                   BusinessApplicationAssetSelectionVO selection, String message) {
        if (objectId != null && objectIds.add(objectId)) {
            selection.getAutoIncludedObjectIds().add(objectId);
            selection.getDependencyMessages().add(message);
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

    record ResolvedSelection(BusinessApplicationAssetSelectionVO selection,
                             List<BusinessApplicationObjectVO> objects,
                             List<AiBusinessApp> entries,
                             List<AiBusinessExtension> extensions,
                             List<AiBusinessProcess> processes) {
    }
}
