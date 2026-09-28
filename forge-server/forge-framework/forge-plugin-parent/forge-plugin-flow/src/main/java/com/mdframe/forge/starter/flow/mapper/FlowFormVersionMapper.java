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

    FlowFormVersion selectLatestByFormId(@Param("formId") Long formId);

    FlowFormVersion selectByIdForRuntime(@Param("id") Long id);

    List<FlowFormVersion> selectVersionsByFormId(@Param("formId") Long formId);
}
