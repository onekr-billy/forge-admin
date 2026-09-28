package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.dto.FlowEntryQueryDTO;
import com.mdframe.forge.starter.flow.entity.FlowEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程入口 Mapper。
 */
@Mapper
public interface FlowEntryMapper extends BaseMapper<FlowEntry> {

    IPage<FlowEntry> selectEntryPage(Page<FlowEntry> page,
                                     @Param("query") FlowEntryQueryDTO query);

    FlowEntry selectByEntryCode(@Param("entryCode") String entryCode);

    Long countByEntryCode(@Param("entryCode") String entryCode,
                          @Param("excludeId") Long excludeId);
}
