package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.dto.FlowFillBatchQueryDTO;
import com.mdframe.forge.starter.flow.entity.FlowFillBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组织填报批次 Mapper。
 */
@Mapper
public interface FlowFillBatchMapper extends BaseMapper<FlowFillBatch> {

    IPage<FlowFillBatch> selectBatchPage(Page<FlowFillBatch> page,
                                         @Param("query") FlowFillBatchQueryDTO query);
}
