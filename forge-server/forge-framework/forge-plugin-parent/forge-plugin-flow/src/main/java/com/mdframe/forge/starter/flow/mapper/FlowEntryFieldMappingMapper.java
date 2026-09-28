package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowEntryFieldMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程入口字段映射 Mapper。
 */
@Mapper
public interface FlowEntryFieldMappingMapper extends BaseMapper<FlowEntryFieldMapping> {

    List<FlowEntryFieldMapping> selectByEntryId(@Param("entryId") Long entryId);

    int deleteByEntryId(@Param("entryId") Long entryId);
}
