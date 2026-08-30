package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.CollaborationDirectoryStatus;
import com.mdframe.forge.plugin.collaboration.domain.CollaborationIssueProcessStatus;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncIssue;
import com.mdframe.forge.plugin.collaboration.domain.model.SyncIssueResolution;
import com.mdframe.forge.plugin.collaboration.mapper.SocialSyncLogMapper;
import com.mdframe.forge.plugin.collaboration.support.CollaborationTenantHelper;
import com.mdframe.forge.plugin.system.entity.SysUser;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.mapper.SysUserSocialMapper;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 目录同步问题单服务（Task 10）。
 * <p>
 * 建单：摘要必须脱敏（禁止明文手机号/邮箱/姓名），同对象待处理问题单去重防抖。
 * 人工处理：权限由管理接口 @SaCheckPermission 控制，处理人/处理时间落库审计；
 * BIND 走 CAS 防并发抢绑，不自动合并身份。
 */
@Slf4j
@Service
@IgnoreTenant
@RequiredArgsConstructor
public class DirectorySyncIssueService {

    private static final String OBJECT_TYPE_USER = "USER";

    private final SocialSyncLogMapper syncLogMapper;
    private final SysUserSocialMapper userSocialMapper;
    private final SysUserMapper userMapper;
    private final ISocialConfigService configService;

    /**
     * 建问题单；同对象已有待处理单时不重复创建
     *
     * @return true 表示本次新建了问题单
     */
    public boolean raiseIssue(Long tenantId, Long connectionId, Long syncLogId,
                              String objectType, String externalId, String issueCode, String issueSummary) {
        SocialSyncIssue pending = syncLogMapper.selectPendingIssueByObject(
                tenantId, connectionId, objectType, externalId);
        if (pending != null) {
            return false;
        }
        SocialSyncIssue issue = new SocialSyncIssue();
        issue.setTenantId(tenantId);
        issue.setConnectionId(connectionId);
        issue.setSyncLogId(syncLogId);
        issue.setObjectType(objectType);
        issue.setExternalId(externalId);
        issue.setIssueCode(issueCode);
        issue.setIssueSummary(issueSummary);
        issue.setProcessStatus(CollaborationIssueProcessStatus.PENDING.getCode());
        issue.setRetryCount(0);
        syncLogMapper.insertIssue(issue);
        log.info("目录同步问题单创建: tenantId={}, connectionId={}, objectType={}, externalId={}, issueCode={}",
                tenantId, connectionId, objectType, externalId, issueCode);
        return true;
    }

    /**
     * 人工处理问题单：BIND人工绑定/IGNORE忽略/RETRY下次同步重试；仅 PENDING 可流转（CAS）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resolveIssue(Long issueId, SyncIssueResolution command, Long operatorId) {
        if (command == null || !StringUtils.hasText(command.action())) {
            throw new BusinessException("处理动作不能为空");
        }
        Long tenantId = CollaborationTenantHelper.currentTenantId();
        SocialSyncIssue issue = syncLogMapper.selectIssueById(issueId, tenantId);
        if (issue == null) {
            throw new BusinessException("问题单不存在");
        }
        if (!CollaborationIssueProcessStatus.PENDING.matches(issue.getProcessStatus())) {
            throw new BusinessException("问题单已处理，禁止重复流转");
        }
        String action = command.action();
        switch (action) {
            case SyncIssueResolution.ACTION_BIND -> {
                if (!OBJECT_TYPE_USER.equals(issue.getObjectType())) {
                    throw new BusinessException("仅用户类问题单支持人工绑定");
                }
                if (command.targetUserId() == null) {
                    throw new BusinessException("人工绑定必须指定目标用户");
                }
                bindExternalUser(issue, command.targetUserId(), tenantId);
                casResolve(issueId, tenantId, CollaborationIssueProcessStatus.RESOLVED.getCode(), action, operatorId);
            }
            case SyncIssueResolution.ACTION_IGNORE -> casResolve(issueId, tenantId, CollaborationIssueProcessStatus.IGNORED.getCode(), action, operatorId);
            case SyncIssueResolution.ACTION_RETRY -> casResolve(issueId, tenantId, CollaborationIssueProcessStatus.RESOLVED.getCode(), action, operatorId);
            default -> throw new BusinessException("不支持的处理动作: " + action);
        }
        log.info("目录同步问题单处理完成: issueId={}, action={}, operatorId={}", issueId, action, operatorId);
    }

    /**
     * 人工绑定外部账号到目标用户：已有绑定走 CAS 防并发抢绑，无绑定则创建同步管理绑定
     */
    private void bindExternalUser(SocialSyncIssue issue, Long targetUserId, Long tenantId) {
        SysUser target = userMapper.selectById(targetUserId);
        if (target == null || target.getDelFlag() != null && target.getDelFlag() != 0
                || !Objects.equals(tenantId, target.getTenantId())) {
            throw new BusinessException("目标用户不存在或不属于当前租户");
        }
        SysUserSocial existingByUser = userSocialMapper.selectBindingByUser(
                tenantId, issue.getConnectionId(), targetUserId);
        if (existingByUser != null && !Objects.equals(existingByUser.getUuid(), issue.getExternalId())) {
            throw new BusinessException("目标用户在该连接下已绑定其他外部账号");
        }
        SysUserSocial binding = userSocialMapper.selectBinding(tenantId, issue.getConnectionId(), issue.getExternalId());
        if (binding == null) {
            SysSocialConfig connection = configService.selectConfigById(issue.getConnectionId());
            if (connection == null) {
                throw new BusinessException("连接不存在: " + issue.getConnectionId());
            }
            SysUserSocial created = new SysUserSocial();
            created.setTenantId(tenantId);
            created.setConnectionId(issue.getConnectionId());
            created.setPlatform(connection.getPlatform());
            created.setExternalEnterpriseId(connection.getEnterpriseId());
            created.setUuid(issue.getExternalId());
            created.setUserId(targetUserId);
            created.setManagedBySync(1);
            created.setExternalStatus(CollaborationDirectoryStatus.ACTIVE.getCode());
            created.setBindTime(LocalDateTime.now());
            created.setLastSyncTime(LocalDateTime.now());
            userSocialMapper.insert(created);
            return;
        }
        if (binding.getUserId() != null && !Objects.equals(binding.getUserId(), targetUserId)) {
            throw new BusinessException("该外部账号已绑定其他用户");
        }
        if (userSocialMapper.bindForgeUserCas(binding.getId(), tenantId, targetUserId) <= 0) {
            throw new BusinessException("绑定失败，可能已被并发处理，请刷新后重试");
        }
        // 人工绑定后收编为同步管理，后续批次纳入 last-seen 维护
        SysUserSocial adopt = new SysUserSocial();
        adopt.setId(binding.getId());
        adopt.setManagedBySync(1);
        adopt.setLastSyncTime(LocalDateTime.now());
        userSocialMapper.updateById(adopt);
    }

    private void casResolve(Long issueId, Long tenantId, String processStatus, String processAction, Long operatorId) {
        if (syncLogMapper.resolveIssue(issueId, tenantId, processStatus, processAction, operatorId) <= 0) {
            throw new BusinessException("问题单已被并发处理，请刷新后重试");
        }
    }
}
