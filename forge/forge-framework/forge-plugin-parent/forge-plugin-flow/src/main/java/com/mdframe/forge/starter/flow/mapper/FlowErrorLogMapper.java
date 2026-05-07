package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程运行错误日志 Mapper
 */
@Mapper
public interface FlowErrorLogMapper extends BaseMapper<FlowErrorLog> {

    IPage<FlowErrorLog> selectErrorLogPage(Page<FlowErrorLog> page,
                                           @Param("processInstanceId") String processInstanceId,
                                           @Param("activityId") String activityId,
                                           @Param("status") Integer status);

    List<FlowErrorLog> selectRecentByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    FlowErrorLog selectLatestUnresolved(@Param("processInstanceId") String processInstanceId,
                                        @Param("activityId") String activityId);

    Long countUnresolvedByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
