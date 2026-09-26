package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.enums.FlowBusinessStatus;
import com.mdframe.forge.starter.flow.spi.FlowMonitorUserLookup;
import com.mdframe.forge.starter.flow.vo.FlowMonitorProcessInstanceVO;
import com.mdframe.forge.starter.flow.vo.FlowMonitorStatisticsVO;
import com.mdframe.forge.starter.flow.vo.FlowMonitorTaskTreeNodeVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowMonitorViewAssemblerTest {

    @Test
    void pendingProcessResolvesCurrentAssigneeDisplayName() {
        FlowBusiness business = business(FlowBusinessStatus.RUNNING);
        business.setCreateTime(LocalDateTime.now().minusHours(2));
        FlowMonitorUserLookup lookup = userId -> "张三";
        ObjectProvider<FlowMonitorUserLookup> provider = provider(lookup);

        FlowMonitorProcessInstanceVO view = FlowMonitorViewAssembler.toAdminProcessInstance(
                business, Map.of("taskName", "经理审批", "assignee", "u1"), provider);

        assertEquals("instance-1", view.getId());
        assertEquals("经理审批", view.getCurrentNode());
        assertEquals("张三", view.getCurrentAssignee());
        assertEquals("2小时", view.getDuration());
    }

    @Test
    void unassignedAndCompletedProcessesRetainFallbacks() {
        FlowBusiness pending = business(FlowBusinessStatus.ACTIVE);
        Map<String, Object> summary = new HashMap<>();
        summary.put("taskName", "认领");
        FlowMonitorProcessInstanceVO unassigned = FlowMonitorViewAssembler.toAdminProcessInstance(
                pending, summary, provider(null));
        assertEquals("待认领", unassigned.getCurrentAssignee());
        assertEquals("-", unassigned.getDuration());

        FlowBusiness completed = business(FlowBusinessStatus.COMPLETED);
        FlowMonitorProcessInstanceVO done = FlowMonitorViewAssembler.toAdminProcessInstance(
                completed, Map.of("taskName", "不应显示", "assignee", "u1"), provider(null));
        assertEquals("-", done.getCurrentNode());
        assertEquals("-", done.getCurrentAssignee());
    }

    @Test
    void lookupFailureFallsBackWithoutBreakingPage() {
        FlowMonitorUserLookup lookup = userId -> { throw new IllegalStateException("lookup unavailable"); };
        FlowMonitorProcessInstanceVO view = FlowMonitorViewAssembler.toAdminProcessInstance(
                business(FlowBusinessStatus.RUNNING),
                Map.of("taskName", "经理审批", "assignee", "u1"), provider(lookup));
        assertEquals("-", view.getCurrentNode());
        assertEquals("-", view.getCurrentAssignee());
    }

    @Test
    void statisticsPreserveNullAndDegradedSemantics() {
        FlowMonitorStatisticsVO statistics = FlowMonitorViewAssembler.toMonitorStatistics(Map.of(
                "runningInstances", 2,
                "pendingTasks", "3",
                "todayCompleted", "invalid",
                "degraded", true,
                "errorCode", "UNAVAILABLE"));
        assertEquals(2L, statistics.getRunningInstances());
        assertEquals(3L, statistics.getPendingTasks());
        assertNull(statistics.getTodayCompleted());
        assertNull(statistics.getTimeoutTasks());
        assertTrue(statistics.getDegraded());
        assertEquals("UNAVAILABLE", statistics.getErrorCode());

        FlowMonitorStatisticsVO degraded = FlowMonitorViewAssembler.degradedAdminStatistics("ERROR");
        assertTrue(degraded.getDegraded());
        assertNull(degraded.getRunningInstances());
        assertEquals("ERROR", degraded.getErrorCode());
        assertFalse(FlowMonitorViewAssembler.toMonitorStatistics(Map.of()).getDegraded());
    }

    @Test
    void taskTreeGroupsSnapshotsInFirstSeenOrder() {
        com.mdframe.forge.starter.flow.entity.FlowTask first = new com.mdframe.forge.starter.flow.entity.FlowTask();
        first.setTaskDefKey("approve");
        first.setTaskName("审批");
        first.setTaskId("task-1");
        first.setAssigneeName("张三");
        com.mdframe.forge.starter.flow.entity.FlowTask second = new com.mdframe.forge.starter.flow.entity.FlowTask();
        second.setTaskDefKey("approve");
        second.setTaskId("task-2");

        List<FlowMonitorTaskTreeNodeVO> tree = FlowMonitorViewAssembler.buildAdminTaskTree(
                List.of(first, second));
        assertEquals(1, tree.size());
        assertEquals("node:approve", tree.get(0).getKey());
        assertEquals("审批", tree.get(0).getLabel());
        assertEquals("张三", tree.get(0).getChildren().get(0).getLabel());
        assertEquals("待签收", tree.get(0).getChildren().get(1).getLabel());
        assertTrue(FlowMonitorViewAssembler.buildAdminTaskTree(List.of()).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FlowMonitorUserLookup> provider(FlowMonitorUserLookup lookup) {
        ObjectProvider<FlowMonitorUserLookup> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lookup);
        return provider;
    }

    private FlowBusiness business(FlowBusinessStatus status) {
        FlowBusiness business = new FlowBusiness();
        business.setProcessInstanceId("instance-1");
        business.setTitle("采购审批");
        business.setStatus(status.getCode());
        return business;
    }
}
