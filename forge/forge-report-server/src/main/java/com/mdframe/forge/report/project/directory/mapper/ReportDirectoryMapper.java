package com.mdframe.forge.report.project.directory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.report.project.directory.domain.ReportDirectory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报表目录 Mapper
 */
@Mapper
public interface ReportDirectoryMapper extends BaseMapper<ReportDirectory> {

    /**
     * 查询全部目录
     */
    List<ReportDirectory> selectDirectoryList();

    /**
     * 查询目录及其全部子目录ID
     */
    List<Long> selectDirectoryAndChildrenIds(@Param("directoryId") Long directoryId);

    /**
     * 查询直属子目录数量
     */
    Long countByParentId(@Param("parentId") Long parentId);

    /**
     * 查询全部子孙目录
     */
    List<ReportDirectory> selectDescendants(@Param("directoryId") Long directoryId);
}
