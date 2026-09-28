package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.entity.FlowForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程表单定义Mapper
 * 
 * @author forge
 */
@Mapper
public interface FlowFormMapper extends BaseMapper<FlowForm> {

    Page<FlowForm> selectFormPage(Page<FlowForm> page,
                                  @Param("formName") String formName,
                                  @Param("status") Integer status);

    List<FlowForm> selectEnabledForms();

    FlowForm selectByFormKey(@Param("formKey") String formKey);

    Long countByFormKey(@Param("formKey") String formKey,
                        @Param("excludeId") Long excludeId);
}
