package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BusinessFlowCommandIdentityTest {

    @Test
    void equivalentTaskPayloadsReuseStableIdentity() {
        Map<String, Object> firstVariables = new LinkedHashMap<>();
        firstVariables.put("amount", 100);
        firstVariables.put("urgent", true);
        Map<String, Object> reorderedVariables = new LinkedHashMap<>();
        reorderedVariables.put("urgent", true);
        reorderedVariables.put("amount", 100);

        BusinessFlowCommandIdentity.Credentials first = BusinessFlowCommandIdentity.forTaskAction(
                "RESUBMIT", 1L, 101L, "task-1", "修改后重提", firstVariables);
        BusinessFlowCommandIdentity.Credentials retry = BusinessFlowCommandIdentity.forTaskAction(
                "RESUBMIT", 1L, 101L, "task-1", "修改后重提", reorderedVariables);

        assertEquals(first, retry);
        assertEquals("flow:" + first.requestDigest(), first.idempotencyKey());
        assertEquals(64, first.requestDigest().length());
    }

    @Test
    void changedProcessCommandPayloadRotatesIdentity() {
        BusinessFlowCommandIdentity.Credentials first = BusinessFlowCommandIdentity.forProcessAction(
                "WITHDRAW", 1L, 101L, "process-1", "申请人撤回");
        BusinessFlowCommandIdentity.Credentials changed = BusinessFlowCommandIdentity.forProcessAction(
                "WITHDRAW", 1L, 101L, "process-1", "信息填写有误");

        assertNotEquals(first, changed);
    }
}
