package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.enums.FlowBusinessStatus;
import com.mdframe.forge.starter.flow.enums.FlowTaskStatus;
import com.mdframe.forge.starter.flow.spi.FlowMonitorUserLookup;
import com.mdframe.forge.starter.flow.vo.FlowMonitorProcessInstanceVO;
import com.mdframe.forge.starter.flow.vo.FlowMonitorStatisticsVO;
import com.mdframe.forge.starter.flow.vo.FlowMonitorTaskTreeItemVO;
import com.mdframe.forge.starter.flow.vo.FlowMonitorTaskTreeNodeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 管理端流程监控的视图转换，不承担租户校验或数据查询。 */
@Slf4j
final class FlowMonitorViewAssembler {

    private FlowMonitorViewAssembler() {
    }

    static FlowMonitorProcessInstanceVO toAdminProcessInstance(
            FlowBusiness business, Map<String, Object> taskSummary,
            ObjectProvider<FlowMonitorUserLookup> userLookupProvider) {
        FlowMonitorProcessInstanceVO item = new FlowMonitorProcessInstanceVO();
        item.setId(business.getProcessInstanceId());
        item.setProcessName(business.getTitle());
        item.setProcessDefKey(business.getProcessDefKey());
        item.setProcessDefName(business.getTitle());
        item.setInitiatorName(business.getApplyUserName());
        item.setInitiatorId(business.getApplyUserId());
        item.setStatus(business.getStatus());
        item.setStartTime(business.getCreateTime());
        item.setBusinessKey(business.getBusinessKey());
        item.setDuration(formatDuration(business.getCreateTime()));

        item.setCurrentNode("-");
        item.setCurrentAssignee("-");
        if (FlowBusinessStatus.isPending(business.getStatus()) && taskSummary != null) {
            try {
                item.setCurrentNode(Objects.toString(taskSummary.get("taskName"), "-"));
                String assignee = Objects.toString(taskSummary.get("assignee"), null);
                if (assignee == null) {
                    item.setCurrentAssignee("待认领");
                } else {
                    FlowMonitorUserLookup userLookup = userLookupProvider.getIfAvailable();
                    String displayName = userLookup == null ? null : userLookup.findDisplayName(assignee);
                    item.setCurrentAssignee(isBlank(displayName) ? assignee : displayName);
                }
            } catch (Exception e) {
                item.setCurrentNode("-");
                item.setCurrentAssignee("-");
                log.warn("查询流程监控当前任务失败：processInstanceId={}", business.getProcessInstanceId(), e);
            }
        }
        return item;
    }

    static FlowMonitorStatisticsVO degradedAdminStatistics(String errorCode) {
        FlowMonitorStatisticsVO statistics = new FlowMonitorStatisticsVO();
        statistics.setDegraded(true);
        statistics.setErrorCode(errorCode);
        return statistics;
    }

    static FlowMonitorStatisticsVO toMonitorStatistics(Map<String, Object> values) {
        FlowMonitorStatisticsVO statistics = new FlowMonitorStatisticsVO();
        statistics.setRunningInstances(toLong(values.get("runningInstances")));
        statistics.setPendingTasks(toLong(values.get("pendingTasks")));
        statistics.setTodayCompleted(toLong(values.get("todayCompleted")));
        statistics.setTimeoutTasks(toLong(values.get("timeoutTasks")));
        statistics.setDegraded(Boolean.TRUE.equals(values.get("degraded")));
        Object errorCode = values.get("errorCode");
        statistics.setErrorCode(errorCode == null ? null : String.valueOf(errorCode));
        return statistics;
    }

    static List<FlowMonitorTaskTreeNodeVO> buildAdminTaskTree(
            List<com.mdframe.forge.starter.flow.entity.FlowTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Map<String, FlowMonitorTaskTreeNodeVO> groups = new LinkedHashMap<>();
        for (com.mdframe.forge.starter.flow.entity.FlowTask task : tasks) {
            if (task == null) {
                continue;
            }
            String nodeKey = isBlank(task.getTaskDefKey()) ? "unknown" : task.getTaskDefKey();
            FlowMonitorTaskTreeNodeVO group = groups.computeIfAbsent(nodeKey, key -> {
                FlowMonitorTaskTreeNodeVO item = new FlowMonitorTaskTreeNodeVO();
                item.setKey("node:" + key);
                item.setLabel(isBlank(task.getTaskName()) ? key : task.getTaskName());
                item.setNodeKey(key);
                item.setChildren(new ArrayList<>());
                return item;
            });
            FlowMonitorTaskTreeItemVO child = new FlowMonitorTaskTreeItemVO();
            child.setKey(task.getTaskId());
            child.setLabel(firstNonBlank(task.getAssigneeName(), task.getAssignee(), "待签收"));
            child.setTaskId(task.getTaskId());
            child.setStatus(task.getStatus());
            child.setActive(FlowTaskStatus.isActionable(task.getStatus()));
            child.setCreateTime(task.getCreateTime());
            child.setCompleteTime(task.getCompleteTime());
            group.getChildren().add(child);
        }
        return new ArrayList<>(groups.values());
    }

    private static String formatDuration(LocalDateTime createTime) {
        if (createTime == null) {
            return "-";
        }
        long startMillis = createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long durationMinutes = (System.currentTimeMillis() - startMillis) / (1000 * 60);
        if (durationMinutes < 60) {
            return durationMinutes + "分钟";
        }
        if (durationMinutes < 24 * 60) {
            return durationMinutes / 60 + "小时";
        }
        return durationMinutes / (24 * 60) + "天";
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
