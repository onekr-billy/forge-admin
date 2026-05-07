package com.mdframe.forge.flow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程错误日志接口
 */
@Slf4j
@RestController
@RequestMapping("/api/flow/monitor")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
@IgnoreTenant
public class FlowErrorLogController {

    private final FlowErrorLogService flowErrorLogService;

    /**
     * 分页查询错误日志
     */
    @GetMapping("/error-logs")
    public RespInfo<Map<String, Object>> getErrorLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) Integer status) {

        Map<String, Object> result = new HashMap<>();
        try {
            Page<FlowErrorLog> pageParam = new Page<>(page, pageSize);
            IPage<FlowErrorLog> pageResult = flowErrorLogService.pageErrors(
                    pageParam, processInstanceId, activityId, status);

            result.put("list", pageResult.getRecords());
            result.put("total", pageResult.getTotal());
        } catch (Exception e) {
            log.error("查询错误日志失败", e);
            result.put("list", java.util.Collections.emptyList());
            result.put("total", 0);
        }
        return RespInfo.success(result);
    }

    /**
     * 获取错误日志详情
     */
    @GetMapping("/error-logs/{logId}")
    public RespInfo<FlowErrorLog> getErrorLogDetail(@PathVariable String logId) {
        FlowErrorLog errorLog = flowErrorLogService.getById(logId);
        if (errorLog == null) {
            return RespInfo.error("错误日志不存在");
        }
        return RespInfo.success(errorLog);
    }

    /**
     * 获取错误日志统计
     */
    @GetMapping("/error-logs/statistics")
    public RespInfo<Map<String, Object>> getErrorLogStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowErrorLog> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

            long total = flowErrorLogService.count();
            statistics.put("total", total);

            wrapper.eq(FlowErrorLog::getStatus, 0);
            long unresolved = flowErrorLogService.count(wrapper);
            statistics.put("unresolved", unresolved);

            wrapper.clear();
            wrapper.eq(FlowErrorLog::getStatus, 1);
            long retried = flowErrorLogService.count(wrapper);
            statistics.put("retried", retried);

            wrapper.clear();
            wrapper.eq(FlowErrorLog::getStatus, 3);
            long retryFailed = flowErrorLogService.count(wrapper);
            statistics.put("retryFailed", retryFailed);
        } catch (Exception e) {
            log.error("获取错误日志统计失败", e);
            statistics.put("total", 0);
            statistics.put("unresolved", 0);
            statistics.put("retried", 0);
            statistics.put("retryFailed", 0);
        }
        return RespInfo.success(statistics);
    }

    /**
     * 重试失败节点
     */
    @PostMapping("/error-logs/{logId}/retry")
    public RespInfo<Void> retryNode(
            @PathVariable String logId,
            @RequestBody Map<String, Object> params) {

        LoginUser loginUser = SessionHelper.getLoginUser();
        String userId = loginUser != null ? String.valueOf(loginUser.getUserId()) : null;
        String processInstanceId = (String) params.get("processInstanceId");
        String activityId = (String) params.get("activityId");
        String reason = (String) params.get("reason");

        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return RespInfo.error("流程实例ID不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            reason = "管理员手动重试";
        }

        flowErrorLogService.retryNode(processInstanceId, activityId, logId, userId, reason);
        return RespInfo.success("重试成功", null);
    }

    /**
     * 解决错误日志（标记为已解决）
     */
    @PutMapping("/error-logs/{logId}/resolve")
    public RespInfo<Void> resolveError(@PathVariable String logId) {
        LoginUser loginUser = SessionHelper.getLoginUser();
        FlowErrorLog errorLog = flowErrorLogService.getById(logId);
        if (errorLog == null) {
            return RespInfo.error("错误日志不存在");
        }

        errorLog.setStatus(2);
        errorLog.setLastRetryUserId(loginUser != null ? String.valueOf(loginUser.getUserId()) : null);
        errorLog.setLastRetryTime(java.time.LocalDateTime.now());
        errorLog.setRetryMessage("管理员手动解决");
        errorLog.setUpdateTime(java.time.LocalDateTime.now());
        flowErrorLogService.updateById(errorLog);

        return RespInfo.success("已标记为已解决", null);
    }
}
