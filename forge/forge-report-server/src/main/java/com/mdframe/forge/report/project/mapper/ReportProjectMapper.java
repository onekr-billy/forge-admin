package com.mdframe.forge.report.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.report.project.domain.ReportProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Go-View 项目 Mapper
 */
@Mapper
public interface ReportProjectMapper extends BaseMapper<ReportProject> {

    /**
     * 分页查询项目
     */
    Page<ReportProject> selectProjectPage(Page<ReportProject> page,
                                          @Param("projectName") String projectName,
                                          @Param("directoryIds") List<Long> directoryIds);

    /**
     * 统计目录下项目数量
     */
    Long countByDirectoryIds(@Param("directoryIds") List<Long> directoryIds);
}
