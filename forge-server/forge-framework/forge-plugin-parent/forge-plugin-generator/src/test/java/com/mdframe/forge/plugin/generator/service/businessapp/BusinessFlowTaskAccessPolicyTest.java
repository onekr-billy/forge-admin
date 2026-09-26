package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("business flow task access policy")
class BusinessFlowTaskAccessPolicyTest {

    private final BusinessFlowTaskAccessPolicy policy = new BusinessFlowTaskAccessPolicy();

    @Test
    @DisplayName("claimed task allows write and hydrates trusted identity")
    void claimedTaskAllowsWrite() {
        BusinessTaskFormContextQueryDTO query = query(" task-1 ");

        policy.validate(query, true, task("42"), true, 42L);

        assertEquals("task-1", query.getTaskId());
        assertEquals("process-1", query.getProcessInstanceId());
        assertEquals("order:1", query.getBusinessKey());
        assertEquals("approve", query.getTaskDefKey());
        assertEquals("orderApproval:3:deployment", query.getProcessDefKey());
    }

    @Test
    @DisplayName("unclaimed candidate may read but must claim before write")
    void candidateReadAndWritePolicy() {
        Map<String, Object> task = task(null);
        task.put("candidateUsers", "7, 42, 99");

        policy.validate(query("task-1"), false, task, true, 42L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> policy.validate(query("task-1"), true, task, true, 42L));

        assertEquals("请先签收任务后再保存业务字段", exception.getMessage());
    }

    @Test
    @DisplayName("processed task cannot open pending form")
    void processedTaskIsRejected() {
        Map<String, Object> task = task("42");
        task.put("status", 2);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> policy.validate(query("task-1"), false, task, true, 42L));

        assertEquals("当前任务已处理，不能访问待办业务表单", exception.getMessage());
    }

    @Test
    @DisplayName("different document key is rejected")
    void crossDocumentRequestIsRejected() {
        BusinessTaskFormContextQueryDTO query = query("task-1");
        query.setBusinessKey("order:2");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> policy.validate(query, false, task("42"), true, 42L));

        assertEquals("业务Key与当前任务不匹配", exception.getMessage());
    }

    @Test
    @DisplayName("retry key is treated as the same document")
    void retryKeyMatchesOriginalDocument() {
        BusinessTaskFormContextQueryDTO query = query("task-1");
        query.setBusinessKey("order:1");
        Map<String, Object> task = task("42");
        task.put("businessKey", "order:1:R2");

        policy.validate(query, false, task, true, 42L);

        assertEquals("order:1", query.getBusinessKey());
    }

    @Test
    @DisplayName("flow test key remains authoritative")
    void syntheticFlowTestKeyRemainsAuthoritative() {
        BusinessTaskFormContextQueryDTO query = query("task-1");
        query.setBusinessKey("order:1");
        Map<String, Object> task = task("42");
        task.put("businessKey", "FLOW_TEST:order_flow:100");

        policy.validate(query, false, task, true, 42L);

        assertEquals("FLOW_TEST:order_flow:100", query.getBusinessKey());
    }

    @Test
    @DisplayName("missing flow service and current user fail closed")
    void requiredInfrastructureFailsClosed() {
        BusinessException noFlow = assertThrows(BusinessException.class,
                () -> policy.validate(query("task-1"), false, task("42"), false, 42L));
        BusinessException noUser = assertThrows(BusinessException.class,
                () -> policy.validate(query("task-1"), false, task("42"), true, null));

        assertEquals("流程服务未配置，无法校验任务身份", noFlow.getMessage());
        assertEquals("当前登录用户不能为空", noUser.getMessage());
    }

    private BusinessTaskFormContextQueryDTO query(String taskId) {
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(taskId);
        return query;
    }

    private Map<String, Object> task(String assignee) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("status", 0);
        task.put("assignee", assignee);
        task.put("processInstanceId", "process-1");
        task.put("businessKey", "order:1");
        task.put("taskDefKey", "approve");
        task.put("processDefKey", "orderApproval:3:deployment");
        return task;
    }
}
