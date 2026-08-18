package com.mdframe.forge.admin.bridge;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mdframe.forge.plugin.generator.service.ApplicationPermissionAdapter;
import com.mdframe.forge.plugin.system.dto.RoleModuleDataScopeDTO;
import com.mdframe.forge.plugin.system.dto.ScopedRolePermissionDTO;
import com.mdframe.forge.plugin.system.dto.SysRoleQuery;
import com.mdframe.forge.plugin.system.entity.SysResource;
import com.mdframe.forge.plugin.system.entity.SysRole;
import com.mdframe.forge.plugin.system.mapper.SysResourceMapper;
import com.mdframe.forge.plugin.system.service.ISysRoleService;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.datascope.entity.SysRoleModuleDataScope;
import com.mdframe.forge.starter.datascope.mapper.SysRoleModuleDataScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用权限工作台的系统角色与资源适配实现。
 */
@Component
@RequiredArgsConstructor
public class ApplicationPermissionAdapterImpl implements ApplicationPermissionAdapter {

    private static final String DEFAULT_CLIENT_CODE = "pc";
    private static final int ROLE_PAGE_SIZE = 100;

    private final ISysRoleService roleService;
    private final SysResourceMapper resourceMapper;
    private final SysRoleModuleDataScopeMapper roleModuleDataScopeMapper;

    @Override
    public List<RoleInfo> listAssignableRoles() {
        List<RoleInfo> result = new ArrayList<>();
        int pageNum = 1;
        long total;
        do {
            SysRoleQuery query = new SysRoleQuery();
            query.setPageNum(pageNum);
            query.setPageSize(ROLE_PAGE_SIZE);
            query.setRoleStatus(1);
            IPage<SysRole> page = roleService.selectRolePage(query);
            page.getRecords().stream().map(this::roleInfo).forEach(result::add);
            total = page.getTotal();
            pageNum++;
        } while (result.size() < total);
        return result;
    }

    @Override
    public Map<String, ResourceInfo> findResourcesByPermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Map.of();
        }
        return resourceMapper.selectByPermissions(resolveTenantId(), permissions).stream()
                .collect(Collectors.toMap(SysResource::getPerms, this::resourceInfo,
                        (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public RoleGrant loadRoleGrant(Long roleId, Set<Long> resourceIds, Set<String> moduleCodes) {
        SysRole role = roleService.selectRoleById(roleId);
        Set<Long> scopeResourceIds = resourceIds == null ? Set.of() : resourceIds;
        Set<Long> selectedResourceIds = roleService.selectRoleResourceIds(
                        roleId, DEFAULT_CLIENT_CODE, true).stream()
                .filter(scopeResourceIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> scopeModuleCodes = moduleCodes == null ? Set.of() : moduleCodes;
        Map<String, Integer> moduleScopes = roleModuleDataScopeMapper
                .selectByRole(role.getTenantId(), role.getId()).stream()
                .filter(item -> scopeModuleCodes.contains(item.getModuleCode()))
                .collect(Collectors.toMap(SysRoleModuleDataScope::getModuleCode,
                        SysRoleModuleDataScope::getDataScope, (left, right) -> right, LinkedHashMap::new));
        return new RoleGrant(roleInfo(role), selectedResourceIds, moduleScopes);
    }

    @Override
    public void saveRoleGrant(Long roleId,
                              Set<Long> scopeResourceIds,
                              Set<Long> selectedResourceIds,
                              Set<String> scopeModuleCodes,
                              Map<String, Integer> moduleScopes) {
        ScopedRolePermissionDTO settings = new ScopedRolePermissionDTO();
        settings.setClientCode(DEFAULT_CLIENT_CODE);
        settings.setScopeResourceIds(new LinkedHashSet<>(scopeResourceIds));
        settings.setSelectedResourceIds(new LinkedHashSet<>(selectedResourceIds));
        settings.setScopeModuleCodes(new LinkedHashSet<>(scopeModuleCodes));
        settings.setModuleScopes(moduleScopes.entrySet().stream().map(entry -> {
            RoleModuleDataScopeDTO scope = new RoleModuleDataScopeDTO();
            scope.setModuleCode(entry.getKey());
            scope.setDataScope(entry.getValue());
            return scope;
        }).toList());
        roleService.saveScopedRolePermissions(roleId, settings);
    }

    private RoleInfo roleInfo(SysRole role) {
        return new RoleInfo(role.getId(), role.getRoleName(), role.getRoleKey(),
                role.getRoleType(), role.getDataScope());
    }

    private ResourceInfo resourceInfo(SysResource resource) {
        return new ResourceInfo(resource.getId(), resource.getParentId(), resource.getResourceType(),
                resource.getResourceName(), resource.getPerms());
    }

    private Long resolveTenantId() {
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("当前租户上下文不能为空");
        }
        return tenantId;
    }
}
