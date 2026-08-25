package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.job.flow.JobFlowBindingSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 流程模型 Mapper
 */
@Mapper
public interface FlowModelMapper extends BaseMapper<FlowModel> {

    /**
     * 分页查询流程模型（支持父级分类查询子级数据）
     */
    IPage<FlowModel> selectModelPage(Page<FlowModel> page, @Param("modelName") String modelName,
                                      @Param("category") String category, @Param("status") Integer status,
                                      @Param("createBy") String createBy);

    /**
     * 按状态统计流程模型数量
     */
    Map<String, Object> selectStatusStatistics(@Param("modelName") String modelName,
                                               @Param("category") String category,
                                               @Param("createBy") String createBy);

    /** 按流程定义 Key 查询模型（通知监听器使用）。 */
    FlowModel selectByModelKey(@Param("modelKey") String modelKey);

    /**
     * 查询当前租户已发布流程模型目录。
     */
    List<FlowModel> selectEnabledModels(@Param("tenantId") Long tenantId,
                                        @Param("category") String category);

    JobFlowBindingSnapshot selectPublishedJobBinding(
            @Param("tenantId") Long tenantId,
            @Param("modelKey") String modelKey,
            @Param("modelVersion") Integer modelVersion);
}
