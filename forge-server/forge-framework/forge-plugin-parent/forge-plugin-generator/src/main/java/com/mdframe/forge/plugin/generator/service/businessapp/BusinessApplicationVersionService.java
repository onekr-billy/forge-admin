package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.generator.service.printing.PrintApplicationVersionGuard;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationVersionMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVersionVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用不可变版本查询和原子提交服务，不暴露历史 update 能力。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationVersionService
        extends ServiceImpl<BusinessApplicationVersionMapper, AiBusinessApplicationVersion> {

    private final BusinessApplicationService applicationService;
    private final BusinessApplicationMapper applicationMapper;
    private final BusinessApplicationSnapshotService snapshotService;
    private final PrintApplicationVersionGuard printVersionGuard;

    public List<BusinessApplicationVersionVO> list(Long applicationId) {
        return baseMapper.selectVersions(requireTenantId(), applicationId).stream()
                .map(version -> toVO(version, false)).toList();
    }

    public BusinessApplicationVersionVO detail(Long applicationId, Integer versionNo) {
        requireTenantId();
        applicationService.requireEntity(applicationId);
        return toVO(requireVersion(applicationId, versionNo), true);
    }

    public AiBusinessApplicationVersion requireVersion(Long applicationId, Integer versionNo) {
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException("应用版本号不正确");
        }
        AiBusinessApplicationVersion version = baseMapper.selectVersion(requireTenantId(), applicationId, versionNo);
        if (version == null) {
            throw new BusinessException("应用发布版本不存在: v" + versionNo);
        }
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiBusinessApplicationVersion commitImmutable(Long applicationId,
                                                        Integer versionNo,
                                                        BusinessApplicationSnapshotService.SnapshotBundle snapshot,
                                                        String publishStatus,
                                                        Integer sourceVersionNo,
                                                        String summary) {
        if (!BusinessApplicationPublishStatus.versionStatuses().contains(publishStatus)) {
            throw new BusinessException("应用发布版本状态不正确");
        }
        IdentitySnapshot identity = requireIdentity();
        // 与模板修改/删除共用应用行锁；同一事务内失败不会插入版本或切换发布指针。
        printVersionGuard.lockAndValidate(applicationId, snapshot.json());
        AiBusinessApplicationVersion existing = baseMapper.selectVersion(
                identity.tenantId(), applicationId, versionNo);
        if (existing != null) {
            if (!StringUtils.equals(existing.getSnapshotHash(), snapshot.hash())) {
                throw new BusinessException("目标应用版本已被其他发布占用");
            }
            markApplicationPublished(identity.tenantId(), applicationId, versionNo, LocalDateTime.now());
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        AiBusinessApplicationVersion version = new AiBusinessApplicationVersion();
        version.setTenantId(identity.tenantId());
        version.setApplicationId(applicationId);
        version.setVersionNo(versionNo);
        version.setSnapshotJson(snapshot.json());
        version.setSnapshotHash(snapshot.hash());
        version.setPublishStatus(publishStatus);
        version.setPublishSummary(StringUtils.abbreviate(StringUtils.trimToNull(summary), 1000));
        version.setSourceVersionNo(sourceVersionNo);
        version.setPublishedBy(identity.userId());
        version.setPublishedTime(now);
        save(version);
        markApplicationPublished(identity.tenantId(), applicationId, versionNo, now);
        return version;
    }

    private void markApplicationPublished(Long tenantId, Long applicationId,
                                          Integer versionNo, LocalDateTime publishTime) {
        if (applicationMapper.markPublished(tenantId, applicationId, versionNo, publishTime) == 0) {
            throw new BusinessException("应用发布状态提交失败");
        }
    }

    private BusinessApplicationVersionVO toVO(AiBusinessApplicationVersion version, boolean includeSnapshot) {
        BusinessApplicationVersionVO vo = new BusinessApplicationVersionVO();
        vo.setId(version.getId());
        vo.setApplicationId(version.getApplicationId());
        vo.setVersionNo(version.getVersionNo());
        vo.setSnapshotHash(version.getSnapshotHash());
        vo.setPublishStatus(version.getPublishStatus());
        vo.setPublishSummary(version.getPublishSummary());
        vo.setSourceVersionNo(version.getSourceVersionNo());
        vo.setPublishedBy(version.getPublishedBy());
        vo.setPublishedTime(version.getPublishedTime());
        vo.setCreateTime(version.getCreateTime());
        if (includeSnapshot) {
            vo.setSnapshot(snapshotService.parse(version.getSnapshotJson()));
        }
        return vo;
    }

    private Long requireTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("应用版本操作缺少可信租户上下文");
        }
        return tenantId;
    }

    private IdentitySnapshot requireIdentity() {
        Long tenantId = requireTenantId();
        Long userId;
        try {
            userId = SessionHelper.getUserId();
        } catch (Exception e) {
            userId = null;
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("应用版本操作缺少可信操作者");
        }
        return new IdentitySnapshot(tenantId, userId);
    }

    private record IdentitySnapshot(Long tenantId, Long userId) {
    }
}
