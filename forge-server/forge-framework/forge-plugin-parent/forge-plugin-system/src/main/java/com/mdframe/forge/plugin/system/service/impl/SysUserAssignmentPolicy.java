package com.mdframe.forge.plugin.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mdframe.forge.plugin.system.constant.SystemConstants;
import com.mdframe.forge.plugin.system.entity.SysOrg;
import com.mdframe.forge.plugin.system.entity.SysPost;
import com.mdframe.forge.plugin.system.entity.SysRole;
import com.mdframe.forge.plugin.system.entity.SysRoleOrg;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.entity.SysUserOrg;
import com.mdframe.forge.plugin.system.entity.SysUserTenant;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysPostMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleMapper;
import com.mdframe.forge.plugin.system.mapper.SysRoleOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户组织、岗位与角色分配策略。
 *
 * <p>集中租户归属、组织适用范围和“用户类型 × 角色数据范围”的 Specification，
 * 用户服务只负责编排绑定命令。</p>
 */
@RequiredArgsConstructor
final class SysUserAssignmentPolicy {

    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final SysOrgMapper orgMapper;
    private final SysPostMapper postMapper;

    int resolveEffectiveUserType(Long userId, Long tenantId) {
        SysUser user = TenantContextHolder.executeIgnore(() -> userMapper.selectById(userId));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (Objects.equals(user.getUserType(), SystemConstants.UserType.SYSTEM_ADMIN)) {
            return SystemConstants.UserType.SYSTEM_ADMIN;
        }
        SysUserTenant member = TenantContextHolder.executeIgnore(() ->
            userTenantMapper.selectOne(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId)
                .eq(SysUserTenant::getStatus, 1)
                .last("LIMIT 1")));
        if (member == null) {
            throw new RuntimeException("目标用户不属于当前租户");
        }
        return Objects.equals(member.getMemberType(), SystemConstants.UserType.TENANT_ADMIN)
            ? SystemConstants.UserType.TENANT_ADMIN
            : SystemConstants.UserType.NORMAL_USER;
    }

    int normalizeUserType(Integer userType) {
        if (userType == null
            || userType < SystemConstants.UserType.SYSTEM_ADMIN
            || userType > SystemConstants.UserType.NORMAL_USER) {
            return SystemConstants.UserType.NORMAL_USER;
        }
        return userType;
    }

    void validateRoleTenant(Long[] roleIds, Long tenantId) {
        if (roleIds == null || roleIds.length == 0) {
            return;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, Arrays.asList(roleIds))
            .eq(SysRole::getTenantId, tenantId));
        if (count == null || count != roleIds.length) {
            throw new RuntimeException("角色不属于当前操作租户");
        }
    }

    List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null) {
            return new ArrayList<>();
        }
        return roleIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    void validateUserOrgMembership(Long userId, Long orgId, Long tenantId) {
        Long count = TenantContextHolder.executeIgnore(() ->
            userOrgMapper.selectCount(new LambdaQueryWrapper<SysUserOrg>()
                .eq(SysUserOrg::getTenantId, tenantId)
                .eq(SysUserOrg::getUserId, userId)
                .eq(SysUserOrg::getOrgId, orgId)));
        if (count == null || count == 0) {
            throw new RuntimeException("用户未加入目标组织");
        }
    }

    void validateRoleStatus(List<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, roleIds)
            .eq(SysRole::getTenantId, tenantId)
            .eq(SysRole::getRoleStatus, 1));
        if (count == null || count != roleIds.size()) {
            throw new RuntimeException("角色不存在或已禁用");
        }
    }

    void validateRolesApplicableToOrg(List<Long> roleIds, Long orgId, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getTenantId, tenantId)
            .in(SysRole::getId, roleIds)
            .select(SysRole::getId, SysRole::getOrgScopeType));
        List<Long> customRoleIds = roles.stream()
            .filter(role -> !isGlobalRoleScope(role))
            .map(SysRole::getId)
            .collect(Collectors.toList());
        if (customRoleIds.isEmpty()) {
            return;
        }
        Long count = roleOrgMapper.selectCount(new LambdaQueryWrapper<SysRoleOrg>()
            .eq(SysRoleOrg::getTenantId, tenantId)
            .eq(SysRoleOrg::getOrgId, orgId)
            .in(SysRoleOrg::getRoleId, customRoleIds));
        if (count == null || count != customRoleIds.size()) {
            throw new RuntimeException("角色不适用于目标组织");
        }
    }

    boolean isRoleApplicableToOrg(Long roleId, Long orgId, Long tenantId) {
        if (roleId == null || orgId == null || tenantId == null) {
            return false;
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getTenantId, tenantId)
            .eq(SysRole::getId, roleId)
            .select(SysRole::getId, SysRole::getOrgScopeType));
        if (isGlobalRoleScope(role)) {
            return true;
        }
        Long count = roleOrgMapper.selectCount(new LambdaQueryWrapper<SysRoleOrg>()
            .eq(SysRoleOrg::getTenantId, tenantId)
            .eq(SysRoleOrg::getRoleId, roleId)
            .eq(SysRoleOrg::getOrgId, orgId));
        return count != null && count > 0;
    }

    void validateOrgTenant(List<Long> orgIds, Long tenantId) {
        List<Long> normalizedOrgIds = normalizeIds(orgIds);
        if (normalizedOrgIds.isEmpty()) {
            return;
        }
        Long count = TenantContextHolder.executeIgnore(() ->
            orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>()
                .in(SysOrg::getId, normalizedOrgIds)
                .eq(SysOrg::getTenantId, tenantId)));
        if (count == null || count != normalizedOrgIds.size()) {
            throw new RuntimeException("组织不属于当前操作租户");
        }
    }

    void validatePostTenant(List<Long> postIds, Long tenantId) {
        List<Long> normalizedPostIds = normalizeIds(postIds);
        if (normalizedPostIds.isEmpty()) {
            return;
        }
        Long count = TenantContextHolder.executeIgnore(() ->
            postMapper.selectCount(new LambdaQueryWrapper<SysPost>()
                .in(SysPost::getId, normalizedPostIds)
                .eq(SysPost::getTenantId, tenantId)));
        if (count == null || count != normalizedPostIds.size()) {
            throw new RuntimeException("岗位不属于当前操作租户");
        }
    }

    void validateRoleDataScopeForTarget(List<Long> roleIds, Long userId, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        int targetUserType = resolveEffectiveUserType(userId, tenantId);
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, roleIds)
            .eq(SysRole::getTenantId, tenantId));
        for (SysRole role : roles) {
            if (!isDataScopeAllowedForUserType(role.getDataScope(), targetUserType)) {
                throw new RuntimeException(buildDataScopeConflictMessage(role, targetUserType));
            }
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        return ids == null ? new ArrayList<>()
            : ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private boolean isGlobalRoleScope(SysRole role) {
        return role != null && Objects.equals(role.getOrgScopeType(), SystemConstants.RoleOrgScope.GLOBAL);
    }

    private String buildDataScopeConflictMessage(SysRole role, int userType) {
        String roleName = role.getRoleName() != null ? role.getRoleName() : String.valueOf(role.getId());
        String scopeLabel = resolveDataScopeLabel(role.getDataScope());
        if (Objects.equals(role.getDataScope(), SystemConstants.RoleDataScope.ALL)) {
            return String.format("角色【%s】的数据范围为【%s】，仅系统管理员类型的用户可绑定，请调整该角色的数据范围后重试",
                roleName, scopeLabel);
        }
        return String.format("角色【%s】的数据范围为【%s】，超出【%s】允许的上限；请调整该角色的数据范围，或将该用户的用户类型调整为【租户管理员】后重试",
            roleName, scopeLabel, resolveUserTypeLabel(userType));
    }

    private String resolveDataScopeLabel(Integer dataScope) {
        if (dataScope == null) {
            return "未设置";
        }
        return switch (dataScope) {
            case SystemConstants.RoleDataScope.ALL -> "全部数据";
            case SystemConstants.RoleDataScope.TENANT -> "本租户数据";
            case SystemConstants.RoleDataScope.ORG -> "本组织数据";
            case SystemConstants.RoleDataScope.ORG_AND_CHILD -> "本组织及子组织";
            case SystemConstants.RoleDataScope.SELF -> "个人数据";
            case SystemConstants.RoleDataScope.REGION -> "本行政区划数据";
            default -> "未知范围(" + dataScope + ")";
        };
    }

    private String resolveUserTypeLabel(int userType) {
        return switch (userType) {
            case SystemConstants.UserType.SYSTEM_ADMIN -> "系统管理员";
            case SystemConstants.UserType.TENANT_ADMIN -> "租户管理员";
            default -> "普通用户";
        };
    }

    private boolean isDataScopeAllowedForUserType(Integer dataScope, int userType) {
        if (dataScope == null || userType == SystemConstants.UserType.SYSTEM_ADMIN) {
            return true;
        }
        if (userType == SystemConstants.UserType.TENANT_ADMIN) {
            return dataScope != SystemConstants.RoleDataScope.ALL;
        }
        return dataScope != SystemConstants.RoleDataScope.ALL
            && dataScope != SystemConstants.RoleDataScope.TENANT;
    }
}
