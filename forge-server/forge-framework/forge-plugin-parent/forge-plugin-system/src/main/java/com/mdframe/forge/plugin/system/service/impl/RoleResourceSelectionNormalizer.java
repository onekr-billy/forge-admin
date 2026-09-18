package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.entity.SysResource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 角色资源选择归一化器。
 * 页面入口需要父级目录构建导航，API/按钮只需要自身用于鉴权。
 */
final class RoleResourceSelectionNormalizer {

    private static final int DIRECTORY_RESOURCE_TYPE = 1;
    private static final int MENU_RESOURCE_TYPE = 2;

    private RoleResourceSelectionNormalizer() {
    }

    static Set<Long> normalize(Set<Long> resourcesWithAncestors,
                               Set<Long> explicitlySelectedResourceIds,
                               Map<Long, SysResource> resourceMap) {
        if (resourcesWithAncestors == null || resourcesWithAncestors.isEmpty()
                || explicitlySelectedResourceIds == null || explicitlySelectedResourceIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> retained = new HashSet<>();
        for (Long resourceId : explicitlySelectedResourceIds) {
            if (!isResourceType(resourceMap.get(resourceId), DIRECTORY_RESOURCE_TYPE)) {
                retained.add(resourceId);
            }
        }

        for (Long resourceId : explicitlySelectedResourceIds) {
            SysResource resource = resourceMap.get(resourceId);
            if (!isResourceType(resource, MENU_RESOURCE_TYPE)) {
                continue;
            }
            Long parentId = normalizeParentId(resource.getParentId());
            while (parentId != 0L
                    && resourcesWithAncestors.contains(parentId)
                    && retained.add(parentId)) {
                SysResource parent = resourceMap.get(parentId);
                parentId = parent == null ? 0L : normalizeParentId(parent.getParentId());
            }
        }
        return retained;
    }

    private static boolean isResourceType(SysResource resource, int resourceType) {
        return resource != null
                && resource.getResourceType() != null
                && resource.getResourceType() == resourceType;
    }

    private static Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }
}
