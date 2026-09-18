package com.mdframe.forge.starter.flow.security;

import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowCcMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskCandidateMapper;
import com.mdframe.forge.starter.flow.entity.FlowTaskCandidate;
import com.mdframe.forge.starter.flow.service.FlowUserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/** Central tenant and participant checks for flow read APIs. */
@Component
@RequiredArgsConstructor
public class FlowAccessGuard {
    private final FlowTaskMapper flowTaskMapper;
    private final FlowBusinessMapper flowBusinessMapper;
    private final FlowCcMapper flowCcMapper;
    private final FlowTaskCandidateMapper flowTaskCandidateMapper;
    private final FlowUserGroupService flowUserGroupService;

    public Long requireTenant() {
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TENANT_REQUIRED");
        }
        return tenantId;
    }

    public String requireUserId() {
        Long userId = SessionHelper.getUserId();
        if (userId == null) {
            throw new IllegalStateException("FLOW_USER_REQUIRED");
        }
        return String.valueOf(userId);
    }

    public FlowTask requireTaskVisible(String taskId) {
        FlowTask task = flowTaskMapper.selectByIdOrTaskIdAndTenant(taskId, requireTenant());
        if (task == null || !isTaskParticipant(task, requireUserId())) {
            throw denied();
        }
        return task;
    }

    public FlowBusiness requireProcessVisible(String processInstanceId) {
        Long tenantId = requireTenant();
        String userId = requireUserId();
        FlowBusiness business = flowBusinessMapper.selectByProcessInstanceIdAndTenantId(processInstanceId, tenantId);
        if (business == null || (!userId.equals(String.valueOf(business.getApplyUserId()))
                && flowTaskMapper.countProcessParticipant(processInstanceId, userId, tenantId) <= 0
                && flowCcMapper.countVisibleByProcess(processInstanceId, userId, tenantId) <= 0)) {
            throw denied();
        }
        return business;
    }

    private boolean isTaskParticipant(FlowTask task, String userId) {
        return userId.equals(String.valueOf(task.getAssignee()))
                || userId.equals(String.valueOf(task.getOwner()))
                || userId.equals(String.valueOf(task.getStartUserId()))
                || containsCsv(task.getCandidateUsers(), userId)
                || hasCandidateRelation(task, FlowTaskCandidate.TYPE_USER, userId)
                || hasCandidateGroup(task);
    }

    private boolean hasCandidateGroup(FlowTask task) {
        String candidateGroups = task.getCandidateGroups();
        Set<String> memberships = sessionGroupMemberships();
        if (!StringUtils.hasText(candidateGroups)) {
            return hasCandidateRelationForSessionGroups(task, memberships);
        }
        for (String group : candidateGroups.split(",")) {
            if (memberships.contains(group.trim())) {
                return true;
            }
        }
        return hasCandidateRelationForSessionGroups(task, memberships);
    }

    /**
     * 会话组（角色/组织/用户组）与任务候选组的命中检查：一次 IN 查询替代逐组 COUNT，
     * 避免可见性检查退化为会话组数量的 N+1 往返。
     */
    private boolean hasCandidateRelationForSessionGroups(FlowTask task, Set<String> memberships) {
        if (flowTaskCandidateMapper == null || task.getTenantId() == null
                || !StringUtils.hasText(task.getTaskId()) || memberships.isEmpty()) {
            return false;
        }
        return flowTaskCandidateMapper.countActiveByTaskAndValues(
                task.getTenantId(), task.getTaskId(), FlowTaskCandidate.TYPE_GROUP, memberships) > 0;
    }

    private boolean hasCandidateRelation(FlowTask task, String candidateType, String candidateValue) {
        return flowTaskCandidateMapper != null
                && task.getTenantId() != null
                && StringUtils.hasText(task.getTaskId())
                && flowTaskCandidateMapper.countActiveByTaskAndValue(
                task.getTenantId(), task.getTaskId(), candidateType, candidateValue) > 0;
    }

    private Set<String> sessionGroupMemberships() {
        Set<String> memberships = new HashSet<>();
        if (SessionHelper.getRoleIds() != null) {
            SessionHelper.getRoleIds().forEach(id -> memberships.add(String.valueOf(id)));
        }
        if (SessionHelper.getRoleKeys() != null) {
            memberships.addAll(SessionHelper.getRoleKeys());
        }
        if (SessionHelper.getOrgIds() != null) {
            SessionHelper.getOrgIds().forEach(id -> memberships.add(String.valueOf(id)));
        }
        Long userId = SessionHelper.getUserId();
        if (userId != null && flowUserGroupService != null) {
            memberships.addAll(flowUserGroupService.resolveGroupCodesByUserId(userId));
        }
        return memberships;
    }

    private boolean containsCsv(String csv, String value) {
        if (!StringUtils.hasText(csv)) return false;
        for (String item : csv.split(",")) if (value.equals(item.trim())) return true;
        return false;
    }

    private BusinessException denied() {
        return new BusinessException(404, "FLOW_RESOURCE_NOT_FOUND");
    }
}
