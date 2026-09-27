package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.system.entity.SysFileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件元数据Mapper
 */
@Mapper
public interface SysFileMetadataMapper extends BaseMapper<SysFileMetadata> {
    
    /**
     * 增加下载次数
     */
    int incrementDownloadCount(@Param("fileId") String fileId);

    Page<SysFileMetadata> selectActivePage(Page<SysFileMetadata> page,
                                           @Param("condition") SysFileMetadata condition,
                                           @Param("currentUserId") Long currentUserId,
                                           @Param("admin") boolean admin);

    List<SysFileMetadata> selectActiveByBusiness(@Param("businessType") String businessType,
                                                 @Param("businessId") String businessId,
                                                 @Param("currentUserId") Long currentUserId,
                                                 @Param("admin") boolean admin);

    SysFileMetadata selectActiveByFileId(@Param("fileId") String fileId);

    SysFileMetadata selectActiveByMd5(@Param("md5") String md5);

    int softDeleteByFileId(@Param("fileId") String fileId);

    int renameActiveFile(@Param("fileId") String fileId,
                         @Param("originalName") String originalName);
}
