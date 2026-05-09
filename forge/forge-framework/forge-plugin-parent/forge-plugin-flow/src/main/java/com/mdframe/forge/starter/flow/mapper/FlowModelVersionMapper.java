package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.entity.FlowModelVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlowModelVersionMapper extends BaseMapper<FlowModelVersion> {

    IPage<FlowModelVersion> pageByVersion(Page<FlowModelVersion> page, @Param("modelId") String modelId);

    Integer getMaxVersion(@Param("modelId") String modelId);

    FlowModelVersion getVersionDetail(@Param("versionId") String versionId);

    FlowModelVersion getVersionByModelAndVersion(@Param("modelId") String modelId, @Param("version") Integer version);
}
