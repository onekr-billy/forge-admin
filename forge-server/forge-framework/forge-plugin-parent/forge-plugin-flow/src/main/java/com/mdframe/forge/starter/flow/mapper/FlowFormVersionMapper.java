package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowFormVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程表单版本 Mapper。
 */
@Mapper
public interface FlowFormVersionMapper extends BaseMapper<FlowFormVersion> {

    FlowFormVersion selectLatestByFormId(@Param("tenantId") Long tenantId,
                                         @Param("formId") Long formId);

    FlowFormVersion selectByIdForRuntimeAndTenant(@Param("id") Long id,
                                                  @Param("tenantId") Long tenantId);

    List<FlowFormVersion> selectVersionsByFormId(@Param("tenantId") Long tenantId,
                                                 @Param("formId") Long formId);
}
