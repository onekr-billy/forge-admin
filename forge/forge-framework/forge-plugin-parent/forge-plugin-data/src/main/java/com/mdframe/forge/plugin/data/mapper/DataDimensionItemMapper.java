package com.mdframe.forge.plugin.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.data.entity.DataDimensionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface DataDimensionItemMapper extends BaseMapper<DataDimensionItem> {

    List<DataDimensionItem> selectItemsByDimensionId(@Param("tenantId") Long tenantId,
        @Param("dimensionId") Long dimensionId,
        @Param("status") Integer status);

    List<DataDimensionItem> selectEnabledItemsByDimensionIds(@Param("tenantId") Long tenantId,
        @Param("dimensionIds") Set<Long> dimensionIds);

    int deleteByDimensionId(@Param("tenantId") Long tenantId, @Param("dimensionId") Long dimensionId);
}
