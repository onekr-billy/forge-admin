package com.mdframe.forge.plugin.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.data.entity.DataDimension;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface DataDimensionMapper extends BaseMapper<DataDimension> {

    IPage<DataDimension> selectDimensionPage(Page<DataDimension> page,
        @Param("tenantId") Long tenantId,
        @Param("dimensionName") String dimensionName,
        @Param("sourceType") String sourceType,
        @Param("status") Integer status);

    List<DataDimension> selectDimensionList(@Param("tenantId") Long tenantId,
        @Param("status") Integer status);

    List<DataDimension> selectDimensionByIds(@Param("tenantId") Long tenantId,
        @Param("dimensionIds") Set<Long> dimensionIds);

    DataDimension selectDimensionByCode(@Param("tenantId") Long tenantId,
        @Param("dimensionCode") String dimensionCode);
}
