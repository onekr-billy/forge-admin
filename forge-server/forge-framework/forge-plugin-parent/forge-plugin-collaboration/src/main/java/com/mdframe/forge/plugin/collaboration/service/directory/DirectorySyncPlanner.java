package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.CollaborationDirectoryStatus;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTag;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryMappingSnapshot;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncPlan;
import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import com.mdframe.forge.starter.collaboration.model.ExternalDepartment;
import com.mdframe.forge.starter.collaboration.model.ExternalTag;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 目录差异规划器（Task 9）。
 * <p>
 * 以 sourceHash 比较计算 CREATE/UPDATE/UNCHANGED；相同快照重复同步产出空计划（零业务更新）。
 * 停用对象不逐行列出：部门/标签由成功批次后的 last-seen SQL 收敛，成员缺失单独给出外部ID列表。
 */
@Component
public class DirectorySyncPlanner {

    /**
     * 对比外部快照与本地映射基线，产出差异计划
     */
    public DirectorySyncPlan plan(DirectorySnapshot snapshot, DirectoryMappingSnapshot current) {
        List<ExternalDepartment> deptCreates = new ArrayList<>();
        List<ExternalDepartment> deptUpdates = new ArrayList<>();
        List<String> deptUnchanged = new ArrayList<>();
        for (ExternalDepartment dept : snapshot.departments()) {
            SocialOrgMapping mapping = current.orgMappings().get(dept.externalId());
            if (mapping == null) {
                deptCreates.add(dept);
            } else if (!Objects.equals(mapping.getSourceHash(), dept.sourceHash())
                    || !CollaborationDirectoryStatus.ACTIVE.matches(mapping.getStatus())) {
                deptUpdates.add(dept);
            } else {
                deptUnchanged.add(dept.externalId());
            }
        }

        List<ExternalUser> userCreates = new ArrayList<>();
        List<ExternalUser> userUpdates = new ArrayList<>();
        List<String> userUnchanged = new ArrayList<>();
        Set<String> seenUserIds = new HashSet<>();
        for (ExternalUser user : snapshot.users()) {
            seenUserIds.add(user.externalUserId());
            SysUserSocial binding = current.userBindings().get(user.externalUserId());
            if (binding == null) {
                userCreates.add(user);
            } else if (!Objects.equals(binding.getSourceHash(), user.sourceHash())) {
                userUpdates.add(user);
            } else {
                userUnchanged.add(user.externalUserId());
            }
        }
        // 由同步管理但未出现在快照中的绑定；仅覆盖成员的范围才判定缺失
        List<String> userMissing = new ArrayList<>();
        if (snapshot.scope() != DirectorySyncScope.TAG_ONLY) {
            for (String uuid : current.userBindings().keySet()) {
                if (!seenUserIds.contains(uuid)) {
                    userMissing.add(uuid);
                }
            }
        }

        List<ExternalTag> tagCreates = new ArrayList<>();
        List<ExternalTag> tagUpdates = new ArrayList<>();
        List<String> tagUnchanged = new ArrayList<>();
        for (ExternalTag tag : snapshot.tags()) {
            SocialTag existing = current.tags().get(tag.externalTagId());
            if (existing == null) {
                tagCreates.add(tag);
            } else if (!Objects.equals(existing.getSourceHash(), tag.sourceHash())
                    || !CollaborationDirectoryStatus.ACTIVE.matches(existing.getStatus())) {
                tagUpdates.add(tag);
            } else {
                tagUnchanged.add(tag.externalTagId());
            }
        }

        return new DirectorySyncPlan(snapshot,
                deptCreates, deptUpdates, deptUnchanged,
                userCreates, userUpdates, userUnchanged, userMissing,
                tagCreates, tagUpdates, tagUnchanged);
    }
}
