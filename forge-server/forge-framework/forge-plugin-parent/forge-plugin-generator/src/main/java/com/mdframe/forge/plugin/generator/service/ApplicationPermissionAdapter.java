package com.mdframe.forge.plugin.generator.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应用权限与系统角色、资源及数据范围之间的隔离适配器。
 */
public interface ApplicationPermissionAdapter {

    List<RoleInfo> listAssignableRoles();

    Map<String, ResourceInfo> findResourcesByPermissions(Set<String> permissions);

    RoleGrant loadRoleGrant(Long roleId, Set<Long> resourceIds, Set<String> moduleCodes);

    void saveRoleGrant(Long roleId,
                       Set<Long> scopeResourceIds,
                       Set<Long> selectedResourceIds,
                       Set<String> scopeModuleCodes,
                       Map<String, Integer> moduleScopes);

    record RoleInfo(Long roleId,
                    String roleName,
                    String roleKey,
                    Integer roleType,
                    Integer defaultDataScope) {
    }

    record ResourceInfo(Long resourceId,
                        Long parentId,
                        Integer resourceType,
                        String resourceName,
                        String permission) {
    }

    record RoleGrant(RoleInfo role,
                     Set<Long> resourceIds,
                     Map<String, Integer> moduleScopes) {
    }
}
