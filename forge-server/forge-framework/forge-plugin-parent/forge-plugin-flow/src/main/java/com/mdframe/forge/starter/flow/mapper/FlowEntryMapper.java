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
                                     @Param("tenantId") Long tenantId,
                                     @Param("query") FlowEntryQueryDTO query);

    FlowEntry selectByIdAndTenant(@Param("id") Long id,
                                  @Param("tenantId") Long tenantId);

    FlowEntry selectByEntryCode(@Param("tenantId") Long tenantId,
                                @Param("entryCode") String entryCode);

    Long countByEntryCode(@Param("tenantId") Long tenantId,
                          @Param("entryCode") String entryCode,
                          @Param("excludeId") Long excludeId);
}
