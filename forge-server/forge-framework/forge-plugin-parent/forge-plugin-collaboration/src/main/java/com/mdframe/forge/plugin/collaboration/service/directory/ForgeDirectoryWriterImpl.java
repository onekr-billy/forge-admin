package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.CollaborationDirectoryStatus;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncPlan;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteContext;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteResult;
import com.mdframe.forge.plugin.collaboration.domain.model.IdentityMatchContext;
import com.mdframe.forge.plugin.collaboration.domain.model.IdentityMatchResult;
import com.mdframe.forge.plugin.collaboration.mapper.SocialDirectoryMappingMapper;
import com.mdframe.forge.plugin.system.entity.SysOrg;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.entity.SysUserOrg;
import com.mdframe.forge.plugin.system.entity.SysUserTenant;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserTenantMapper;
import com.mdframe.forge.starter.auth.util.PasswordUtil;
import com.mdframe.forge.starter.collaboration.model.ExternalDepartment;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.mapper.SysUserSocialMapper;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * Forge 目录写入适配器实现（Task 10）。
 * <p>
 * 部门阶段与用户阶段各自独立事务；按来源所有权写入：
 * <ul>
 *     <li>目录权威为 EXTERNAL 时才更新组织名称/层级与用户资料字段，映射/绑定维护不受权威限制</li>
 *     <li>用户组织绑定只增不删，角色/岗位/密码/状态等本地资产一律不触碰</li>
 *     <li>手机号/邮箱冲突不自动合并，统一建问题单人工处理</li>
 * </ul>
 */
@Slf4j
@Service
@IgnoreTenant
@RequiredArgsConstructor
public class ForgeDirectoryWriterImpl implements ForgeDirectoryWriter {

    private static final String OBJECT_TYPE_DEPT = "DEPT";
    private static final String OBJECT_TYPE_USER = "USER";
    private static final String ISSUE_DEPT_MAPPING_MISSING = "DEPT_MAPPING_MISSING";
    private static final String ISSUE_USER_BINDING_MISSING = "USER_BINDING_MISSING";
    private static final String ISSUE_USERNAME_CONFLICT = "USERNAME_CONFLICT";
    private static final String AUTHORITY_EXTERNAL = "EXTERNAL";
    private static final String ROOT_ANCESTORS = "0";

    private final ISocialConfigService configService;
    private final SocialDirectoryMappingMapper mappingMapper;
    private final SysUserSocialMapper userSocialMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final UserIdentityMatchPolicy identityMatchPolicy;
    private final DirectorySyncIssueService issueService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public DirectoryWriteResult apply(DirectorySyncPlan plan, DirectoryWriteContext context) {
        boolean deptEmpty = plan.deptCreates().isEmpty() && plan.deptUpdates().isEmpty();
        boolean userEmpty = plan.userCreates().isEmpty() && plan.userUpdates().isEmpty();
        if (deptEmpty && userEmpty) {
            return DirectoryWriteResult.empty();
        }
        SysSocialConfig connection = configService.selectConfigById(context.connectionId());
        if (connection == null) {
            throw new BusinessException("企业协同连接不存在: " + context.connectionId());
        }
        boolean externalAuthoritative = AUTHORITY_EXTERNAL.equals(context.directoryAuthority());
        WriteCounter counter = new WriteCounter();
        if (!deptEmpty) {
            transactionTemplate.executeWithoutResult(status ->
                    applyDepartments(plan, context, externalAuthoritative, counter));
        }
        if (!userEmpty) {
            transactionTemplate.executeWithoutResult(status ->
                    applyUsers(plan, context, connection, externalAuthoritative, counter));
        }
        log.info("目录写入完成: connectionId={}, runId={}, created={}, updated={}, issues={}",
                context.connectionId(), context.syncLogId(), counter.created, counter.updated, counter.issues);
        return new DirectoryWriteResult(counter.created, counter.updated, counter.issues);
    }

    // ==================== 部门阶段 ====================

    private void applyDepartments(DirectorySyncPlan plan, DirectoryWriteContext context,
                                  boolean externalAuthoritative, WriteCounter counter) {
        List<SocialOrgMapping> existing = mappingMapper.selectOrgMappings(
                context.tenantId(), context.connectionId(), null);
        Map<String, SocialOrgMapping> mappingByExternal = existing.stream()
                .collect(Collectors.toMap(SocialOrgMapping::getExternalDeptId, Function.identity(), (a, b) -> a));
        Map<String, Long> orgIdByExternal = new HashMap<>();
        existing.stream()
                .filter(m -> m.getOrgId() != null)
                .forEach(m -> orgIdByExternal.put(m.getExternalDeptId(), m.getOrgId()));
        Map<Long, String> ancestorsCache = new HashMap<>();

        createDepartments(plan.deptCreates(), context, orgIdByExternal, ancestorsCache, counter);
        updateDepartments(plan, context, externalAuthoritative,
                mappingByExternal, orgIdByExternal, ancestorsCache, counter);
    }

    /**
     * 拓扑序创建部门：父可解析才创建；一轮无进展时兜底挂到默认组织，避免死循环
     */
    private void createDepartments(List<ExternalDepartment> creates, DirectoryWriteContext context,
                                   Map<String, Long> orgIdByExternal, Map<Long, String> ancestorsCache,
                                   WriteCounter counter) {
        if (creates.isEmpty()) {
            return;
        }
        List<ExternalDepartment> remaining = new ArrayList<>(creates);
        Set<String> pendingIds = remaining.stream()
                .map(ExternalDepartment::externalId)
                .collect(Collectors.toSet());
        List<SocialOrgMapping> newMappings = new ArrayList<>();
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            Iterator<ExternalDepartment> it = remaining.iterator();
            while (it.hasNext()) {
                ExternalDepartment dept = it.next();
                String parentExternalId = dept.parentExternalId();
                Long parentOrgId;
                if (!StringUtils.hasText(parentExternalId)) {
                    parentOrgId = defaultParentOrgId(context);
                } else if (orgIdByExternal.containsKey(parentExternalId)) {
                    parentOrgId = orgIdByExternal.get(parentExternalId);
                } else if (pendingIds.contains(parentExternalId)) {
                    // 父部门在本批待创建，等下一轮
                    continue;
                } else {
                    // 父部门既无映射也不在计划内，防御性挂默认组织
                    log.warn("部门 {} 的父部门 {} 无法解析，挂载到默认组织", dept.externalId(), parentExternalId);
                    parentOrgId = defaultParentOrgId(context);
                }
                Long orgId = createOrg(dept, parentOrgId, context.tenantId(), ancestorsCache);
                orgIdByExternal.put(dept.externalId(), orgId);
                pendingIds.remove(dept.externalId());
                newMappings.add(buildMapping(dept, orgId, context));
                counter.created++;
                it.remove();
                progressed = true;
            }
            if (!progressed) {
                // 剩余部门互相成环或父级异常，全部兜底挂默认组织
                for (ExternalDepartment dept : remaining) {
                    log.warn("部门 {} 拓扑无法收敛，挂载到默认组织", dept.externalId());
                    Long orgId = createOrg(dept, defaultParentOrgId(context), context.tenantId(), ancestorsCache);
                    orgIdByExternal.put(dept.externalId(), orgId);
                    newMappings.add(buildMapping(dept, orgId, context));
                    counter.created++;
                }
                remaining.clear();
            }
        }
        if (!newMappings.isEmpty()) {
            mappingMapper.insertOrgMappings(newMappings);
        }
    }

    private void updateDepartments(DirectorySyncPlan plan, DirectoryWriteContext context,
                                   boolean externalAuthoritative,
                                   Map<String, SocialOrgMapping> mappingByExternal,
                                   Map<String, Long> orgIdByExternal,
                                   Map<Long, String> ancestorsCache, WriteCounter counter) {
        Map<String, List<ExternalDepartment>> childrenIndex = plan.snapshot().departments().stream()
                .filter(d -> StringUtils.hasText(d.parentExternalId()))
                .collect(Collectors.groupingBy(ExternalDepartment::parentExternalId));
        for (ExternalDepartment dept : plan.deptUpdates()) {
            SocialOrgMapping mapping = mappingByExternal.get(dept.externalId());
            if (mapping == null || mapping.getOrgId() == null) {
                if (issueService.raiseIssue(context.tenantId(), context.connectionId(), context.syncLogId(),
                        OBJECT_TYPE_DEPT, dept.externalId(), ISSUE_DEPT_MAPPING_MISSING,
                        "部门映射缺失，无法更新对应组织，需人工排查")) {
                    counter.issues++;
                }
                continue;
            }
            if (externalAuthoritative) {
                syncOrgFields(dept, mapping.getOrgId(), context,
                        orgIdByExternal, ancestorsCache, childrenIndex, mappingByExternal);
            }
            mapping.setExternalParentId(dept.parentExternalId());
            mapping.setExternalDeptName(dept.name());
            mapping.setSourceHash(dept.sourceHash());
            mapping.setLastSeenRunId(context.syncLogId());
            mapping.setStatus(CollaborationDirectoryStatus.ACTIVE.getCode());
            mappingMapper.updateOrgMappingByExternalId(mapping);
            counter.updated++;
        }
    }

    /**
     * 外部权威时同步组织名称/父级/排序；父级变化时重算自身与已映射后代的 ancestors
     */
    private void syncOrgFields(ExternalDepartment dept, Long orgId, DirectoryWriteContext context,
                               Map<String, Long> orgIdByExternal, Map<Long, String> ancestorsCache,
                               Map<String, List<ExternalDepartment>> childrenIndex,
                               Map<String, SocialOrgMapping> mappingByExternal) {
        SysOrg current = orgMapper.selectById(orgId);
        if (current == null) {
            return;
        }
        Long newParentId;
        if (!StringUtils.hasText(dept.parentExternalId())) {
            newParentId = defaultParentOrgId(context);
        } else {
            Long mappedParent = orgIdByExternal.get(dept.parentExternalId());
            // 父部门未映射时保持现状，避免误挂
            newParentId = mappedParent != null ? mappedParent : current.getParentId();
        }
        String newAncestors = buildAncestors(newParentId, ancestorsCache);
        Integer sort = dept.orderNum() == null ? null : dept.orderNum().intValue();
        orgMapper.updateOrgSyncFields(context.tenantId(), orgId, dept.name(), newParentId, newAncestors, sort);
        ancestorsCache.put(orgId, newAncestors);
        if (!Objects.equals(current.getParentId(), newParentId)) {
            recomputeDescendantAncestors(dept.externalId(), orgId, newAncestors,
                    childrenIndex, mappingByExternal, ancestorsCache, context);
        }
    }

    /**
     * 部门移动后按快照子树递归重算已映射后代的 ancestors（仅同步拥有的组织）
     */
    private void recomputeDescendantAncestors(String externalId, Long orgId, String orgAncestors,
                                              Map<String, List<ExternalDepartment>> childrenIndex,
                                              Map<String, SocialOrgMapping> mappingByExternal,
                                              Map<Long, String> ancestorsCache, DirectoryWriteContext context) {
        List<ExternalDepartment> children = childrenIndex.get(externalId);
        if (children == null || children.isEmpty()) {
            return;
        }
        String childAncestors = orgAncestors + "," + orgId;
        for (ExternalDepartment child : children) {
            SocialOrgMapping childMapping = mappingByExternal.get(child.externalId());
            if (childMapping == null || childMapping.getOrgId() == null) {
                continue;
            }
            orgMapper.updateOrgSyncFields(context.tenantId(), childMapping.getOrgId(),
                    null, orgId, childAncestors, null);
            ancestorsCache.put(childMapping.getOrgId(), childAncestors);
            recomputeDescendantAncestors(child.externalId(), childMapping.getOrgId(), childAncestors,
                    childrenIndex, mappingByExternal, ancestorsCache, context);
        }
    }

    private Long createOrg(ExternalDepartment dept, Long parentOrgId, Long tenantId,
                           Map<Long, String> ancestorsCache) {
        SysOrg org = new SysOrg();
        org.setTenantId(tenantId);
        org.setOrgName(dept.name());
        org.setParentId(parentOrgId == null ? 0L : parentOrgId);
        org.setAncestors(buildAncestors(parentOrgId, ancestorsCache));
        org.setSort(dept.orderNum() == null ? 0 : dept.orderNum().intValue());
        org.setOrgStatus(1);
        orgMapper.insert(org);
        ancestorsCache.put(org.getId(), org.getAncestors());
        return org.getId();
    }

    private String buildAncestors(Long parentOrgId, Map<Long, String> ancestorsCache) {
        if (parentOrgId == null || parentOrgId == 0L) {
            return ROOT_ANCESTORS;
        }
        String parentAncestors = ancestorsCache.get(parentOrgId);
        if (parentAncestors == null) {
            SysOrg parent = orgMapper.selectById(parentOrgId);
            parentAncestors = parent == null || !StringUtils.hasText(parent.getAncestors())
                    ? ROOT_ANCESTORS : parent.getAncestors();
            ancestorsCache.put(parentOrgId, parentAncestors);
        }
        return parentAncestors + "," + parentOrgId;
    }

    private Long defaultParentOrgId(DirectoryWriteContext context) {
        return context.defaultOrgId() != null ? context.defaultOrgId() : 0L;
    }

    private SocialOrgMapping buildMapping(ExternalDepartment dept, Long orgId, DirectoryWriteContext context) {
        SocialOrgMapping mapping = new SocialOrgMapping();
        mapping.setTenantId(context.tenantId());
        mapping.setConnectionId(context.connectionId());
        mapping.setExternalDeptId(dept.externalId());
        mapping.setExternalParentId(dept.parentExternalId());
        mapping.setExternalDeptName(dept.name());
        mapping.setOrgId(orgId);
        mapping.setSourceHash(dept.sourceHash());
        mapping.setLastSeenRunId(context.syncLogId());
        mapping.setStatus(CollaborationDirectoryStatus.ACTIVE.getCode());
        return mapping;
    }

    // ==================== 用户阶段 ====================

    private void applyUsers(DirectorySyncPlan plan, DirectoryWriteContext context,
                            SysSocialConfig connection, boolean externalAuthoritative, WriteCounter counter) {
        // 部门阶段已提交，重新加载映射用于用户挂组织
        Map<String, Long> orgIdByExternal = mappingMapper.selectOrgMappings(
                        context.tenantId(), context.connectionId(), null).stream()
                .filter(m -> m.getOrgId() != null)
                .collect(Collectors.toMap(SocialOrgMapping::getExternalDeptId,
                        SocialOrgMapping::getOrgId, (a, b) -> a));
        IdentityMatchContext matchContext = new IdentityMatchContext(
                context.tenantId(), context.connectionId(), context.identityPolicy());

        for (ExternalUser user : plan.userCreates()) {
            IdentityMatchResult match = identityMatchPolicy.resolve(user, matchContext);
            switch (match.decision()) {
                case UPDATE_BOUND -> {
                    adoptBinding(match.bindingId(), user);
                    if (externalAuthoritative) {
                        updateProfile(match.forgeUserId(), user, context);
                    }
                    ensureUserOrgs(match.forgeUserId(), user, orgIdByExternal, context);
                    counter.updated++;
                }
                case CREATE_NEW -> {
                    if (createUserWithBinding(user, connection, context, orgIdByExternal, counter)) {
                        counter.created++;
                    }
                }
                case RAISE_ISSUE -> {
                    if (issueService.raiseIssue(context.tenantId(), context.connectionId(), context.syncLogId(),
                            OBJECT_TYPE_USER, user.externalUserId(), match.issueCode(), match.issueSummary())) {
                        counter.issues++;
                    }
                }
                case SKIP -> log.debug("BIND_ONLY 策略跳过外部成员: {}", user.externalUserId());
            }
        }

        if (!plan.userUpdates().isEmpty()) {
            Map<String, SysUserSocial> managedByUuid = mappingMapper.selectSyncManagedUserBindings(
                            context.tenantId(), context.connectionId()).stream()
                    .collect(Collectors.toMap(SysUserSocial::getUuid, Function.identity(), (a, b) -> a));
            for (ExternalUser user : plan.userUpdates()) {
                SysUserSocial binding = managedByUuid.get(user.externalUserId());
                if (binding == null || binding.getUserId() == null) {
                    if (issueService.raiseIssue(context.tenantId(), context.connectionId(), context.syncLogId(),
                            OBJECT_TYPE_USER, user.externalUserId(), ISSUE_USER_BINDING_MISSING,
                            "同步管理的用户绑定缺失，需人工排查")) {
                        counter.issues++;
                    }
                    continue;
                }
                if (externalAuthoritative) {
                    updateProfile(binding.getUserId(), user, context);
                }
                adoptBinding(binding.getId(), user);
                ensureUserOrgs(binding.getUserId(), user, orgIdByExternal, context);
                counter.updated++;
            }
        }
    }

    /**
     * 收编/刷新绑定：同步管理标记、快照哈希、外部状态与展示资料（不写 token）
     */
    private void adoptBinding(Long bindingId, ExternalUser user) {
        SysUserSocial update = new SysUserSocial();
        update.setId(bindingId);
        update.setManagedBySync(1);
        update.setExternalStatus(user.active()
                ? CollaborationDirectoryStatus.ACTIVE.getCode()
                : CollaborationDirectoryStatus.DISABLED.getCode());
        update.setSourceHash(user.sourceHash());
        update.setLastSyncTime(LocalDateTime.now());
        update.setNickname(user.name());
        update.setAvatar(user.avatar());
        update.setEmail(user.email());
        userSocialMapper.updateById(update);
    }

    private void updateProfile(Long userId, ExternalUser user, DirectoryWriteContext context) {
        userMapper.updateUserProfileBySync(context.tenantId(), userId,
                user.name(), user.avatar(),
                StringUtils.hasText(user.email()) ? user.email() : null,
                StringUtils.hasText(user.mobile()) ? user.mobile() : null);
    }

    /**
     * AUTO_CREATE 策略新建用户：随机密码 + 强制首登改密；用户名冲突建问题单不落库
     */
    private boolean createUserWithBinding(ExternalUser user, SysSocialConfig connection,
                                          DirectoryWriteContext context,
                                          Map<String, Long> orgIdByExternal, WriteCounter counter) {
        String username = resolveUsername(user.externalUserId(), context);
        if (username == null) {
            if (issueService.raiseIssue(context.tenantId(), context.connectionId(), context.syncLogId(),
                    OBJECT_TYPE_USER, user.externalUserId(), ISSUE_USERNAME_CONFLICT,
                    "自动创建用户时用户名冲突，需人工绑定或调整")) {
                counter.issues++;
            }
            return false;
        }
        SysUser sysUser = new SysUser();
        sysUser.setTenantId(context.tenantId());
        sysUser.setUsername(username);
        sysUser.setRealName(user.name());
        sysUser.setUserType(2);
        sysUser.setEmail(StringUtils.hasText(user.email()) ? user.email() : null);
        sysUser.setPhone(StringUtils.hasText(user.mobile()) ? user.mobile() : null);
        sysUser.setAvatar(user.avatar());
        sysUser.setPassword(PasswordUtil.encrypt(UUID.randomUUID().toString()));
        sysUser.setForcePasswordChange(true);
        sysUser.setUserStatus(user.active() ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        userMapper.insert(sysUser);

        SysUserTenant userTenant = new SysUserTenant();
        userTenant.setTenantId(context.tenantId());
        userTenant.setUserId(sysUser.getId());
        userTenant.setMemberType(2);
        userTenant.setIsDefault(1);
        userTenant.setStatus(EnableStatus.ENABLED.getCode());
        userTenantMapper.insert(userTenant);

        SysUserSocial binding = new SysUserSocial();
        binding.setTenantId(context.tenantId());
        binding.setConnectionId(context.connectionId());
        binding.setPlatform(connection.getPlatform());
        binding.setExternalEnterpriseId(connection.getEnterpriseId());
        binding.setUuid(user.externalUserId());
        binding.setUserId(sysUser.getId());
        binding.setManagedBySync(1);
        binding.setExternalStatus(user.active()
                ? CollaborationDirectoryStatus.ACTIVE.getCode()
                : CollaborationDirectoryStatus.DISABLED.getCode());
        binding.setSourceHash(user.sourceHash());
        binding.setNickname(user.name());
        binding.setAvatar(user.avatar());
        binding.setEmail(user.email());
        binding.setBindTime(LocalDateTime.now());
        binding.setLastSyncTime(LocalDateTime.now());
        userSocialMapper.insert(binding);

        ensureUserOrgs(sysUser.getId(), user, orgIdByExternal, context);
        return true;
    }

    /**
     * 用户名生成：外部ID → 冲突加连接后缀 → 仍冲突返回 null 交由问题单处理
     */
    private String resolveUsername(String externalUserId, DirectoryWriteContext context) {
        if (userMapper.selectByUsernameForLogin(externalUserId, context.tenantId()) == null) {
            return externalUserId;
        }
        String fallback = externalUserId + "_" + context.connectionId();
        if (userMapper.selectByUsernameForLogin(fallback, context.tenantId()) == null) {
            return fallback;
        }
        return null;
    }

    /**
     * 用户组织绑定只增不删：外部部门映射到的组织缺绑则补，手工绑定的组织不动
     */
    private void ensureUserOrgs(Long userId, ExternalUser user,
                                Map<String, Long> orgIdByExternal, DirectoryWriteContext context) {
        List<Long> targetOrgIds = user.departmentIds().stream()
                .map(orgIdByExternal::get)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (targetOrgIds.isEmpty() && context.defaultOrgId() != null) {
            targetOrgIds = List.of(context.defaultOrgId());
        }
        if (targetOrgIds.isEmpty()) {
            return;
        }
        Set<Long> bound = new HashSet<>(userOrgMapper.selectOrgIdsByUserTenant(context.tenantId(), userId));
        boolean hasMain = !bound.isEmpty();
        for (Long orgId : targetOrgIds) {
            if (bound.contains(orgId)) {
                continue;
            }
            SysUserOrg rel = new SysUserOrg();
            rel.setTenantId(context.tenantId());
            rel.setUserId(userId);
            rel.setOrgId(orgId);
            rel.setIsMain(hasMain ? 0 : 1);
            userOrgMapper.insert(rel);
            bound.add(orgId);
            hasMain = true;
        }
    }

    /**
     * 阶段间累计写入计数
     */
    private static final class WriteCounter {
        private int created;
        private int updated;
        private int issues;
    }
}
