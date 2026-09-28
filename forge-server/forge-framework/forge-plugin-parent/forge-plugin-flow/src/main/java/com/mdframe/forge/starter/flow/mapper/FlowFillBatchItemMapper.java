package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowFillBatchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 组织填报批次明细 Mapper。
 */
@Mapper
public interface FlowFillBatchItemMapper extends BaseMapper<FlowFillBatchItem> {

    List<FlowFillBatchItem> selectByBatchId(@Param("tenantId") Long tenantId,
                                           @Param("batchId") Long batchId);

    FlowFillBatchItem selectByIdForUpdateAndTenant(@Param("id") Long id,
                                                   @Param("tenantId") Long tenantId);

    int deleteByProcessInstanceIdLogically(@Param("processInstanceId") String processInstanceId,
                                           @Param("tenantId") Long tenantId);
}
