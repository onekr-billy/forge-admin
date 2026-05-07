package com.mdframe.forge.starter.flow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;

import java.util.List;

/**
 * 流程运行错误日志服务
 */
public interface FlowErrorLogService extends IService<FlowErrorLog> {

    void recordError(FlowErrorLog errorLog, Throwable throwable);

    IPage<FlowErrorLog> pageErrors(Page<FlowErrorLog> page, String processInstanceId, String activityId, Integer status);

    List<FlowErrorLog> listRecentByProcessInstanceId(String processInstanceId);

    Long countUnresolvedByProcessInstanceId(String processInstanceId);

    void retryNode(String processInstanceId, String activityId, String logId, String userId, String reason);
}
