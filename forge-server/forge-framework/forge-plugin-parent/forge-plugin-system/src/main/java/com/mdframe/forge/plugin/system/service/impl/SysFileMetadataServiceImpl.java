package com.mdframe.forge.plugin.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.system.entity.SysFileMetadata;
import com.mdframe.forge.plugin.system.entity.SysFileStorageConfig;
import com.mdframe.forge.plugin.system.mapper.SysFileMetadataMapper;
import com.mdframe.forge.plugin.system.service.ISysFileMetadataService;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.file.core.FileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * 文件元数据Service实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileMetadataServiceImpl extends ServiceImpl<SysFileMetadataMapper, SysFileMetadata>
        implements ISysFileMetadataService {
    
    private final FileManager fileManager;
    
    @Override
    public Page<SysFileMetadata> page(PageQuery query, SysFileMetadata condition) {
        Page<SysFileMetadata> page = new Page<>(query.getPageNum(), query.getPageSize());
        boolean admin = StpUtil.hasPermission("*:*:*");
        Long currentUserId = admin ? null : StpUtil.getLoginIdAsLong();
        return this.baseMapper.selectActivePage(page, condition, currentUserId, admin);
    }
    
    @Override
    public List<SysFileMetadata> listByBusiness(String businessType, String businessId) {
        boolean admin = StpUtil.hasPermission("*:*:*");
        Long currentUserId = admin ? null : StpUtil.getLoginIdAsLong();
        return this.baseMapper.selectActiveByBusiness(businessType, businessId, currentUserId, admin);
    }
    
    @Override
    public SysFileMetadata getByFileId(String fileId) {
        SysFileMetadata metadata = this.baseMapper.selectActiveByFileId(fileId);
        checkReadAccess(metadata);
        return metadata;
    }

    @Override
    public SysFileMetadata getById(Serializable id) {
        SysFileMetadata metadata = super.getById(id);
        checkReadAccess(metadata);
        return metadata;
    }

    private void checkOwnership(SysFileMetadata metadata) {
        if (metadata == null) {
            throw new BusinessException("素材不存在");
        }
        if (StpUtil.hasPermission("*:*:*")) {
            return;
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (metadata.getUploaderId() == null || !currentUserId.equals(metadata.getUploaderId())) {
            throw new BusinessException(403, "无权操作他人素材");
        }
    }

    private void checkReadAccess(SysFileMetadata metadata) {
        if (metadata != null && Boolean.TRUE.equals(metadata.getIsPrivate())) {
            checkOwnership(metadata);
        }
    }

    @Override
    public void removeByFileId(String fileId) {
        SysFileMetadata metadata = this.baseMapper.selectActiveByFileId(fileId);
        checkOwnership(metadata);
        // 文件 IO 在事务外执行，避免长事务占用 DB 连接
        fileManager.delete(metadata.getFileId());
    }

    @Override
    public void removeBatch(String[] fileIds) {
        for (String fileId : fileIds) {
            SysFileMetadata fileMetadata = this.baseMapper.selectActiveByFileId(fileId);
            if (fileMetadata == null) {
                continue;
            }
            checkOwnership(fileMetadata);
            try {
                fileManager.delete(fileMetadata.getFileId());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("删除文件失败: {}", fileId, e);
            }
        }
    }

    @Override
    public boolean updateById(SysFileMetadata metadata) {
        if (metadata != null && metadata.getId() != null) {
            checkOwnership(this.getById(metadata.getId()));
            // status 专用于逻辑删除，只允许删除接口修改。
            metadata.setStatus(null);
        }
        return super.updateById(metadata);
    }

    @Override
    public void rename(String fileId, String originalName) {
        SysFileMetadata existing = this.baseMapper.selectActiveByFileId(fileId);
        checkOwnership(existing);
        this.baseMapper.renameActiveFile(fileId, originalName);
    }
}
