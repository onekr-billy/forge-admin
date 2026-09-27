package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowTaskIdentityResolverTest {

    @Test
    void numericUserIdPassesThroughWithoutOrgLookup() {
        FlowOrgIntegrationService org = mock(FlowOrgIntegrationService.class);

        assertEquals("42", FlowTaskIdentityResolver.normalizeUserId(org, " 42 ", "task-1", "assignee"));
        verifyNoInteractions(org);
    }

    @Test
    void uniqueExactDisplayNameResolvesToIdButAmbiguityDoesNot() {
        FlowOrgIntegrationService org = mock(FlowOrgIntegrationService.class);
        when(org.getUserList("张三", null)).thenReturn(List.of(
                Map.of("id", "42", "realName", "张三"),
                Map.of("id", "43", "realName", "李四")));
        when(org.getUserList("同名", null)).thenReturn(List.of(
                Map.of("id", "42", "realName", "同名"),
                Map.of("id", "43", "realName", "同名")));

        assertEquals("42", FlowTaskIdentityResolver.normalizeUserId(org, "张三", "task-1", "assignee"));
        assertEquals("同名", FlowTaskIdentityResolver.normalizeUserId(org, "同名", "task-1", "owner"));
    }

    @Test
    void storedDisplayNameWinsAndMissingNameUsesOrgProfile() {
        FlowOrgIntegrationService org = mock(FlowOrgIntegrationService.class);
        when(org.getUserInfo("42")).thenReturn(Map.of("realName", "张三"));

        assertEquals("已有姓名", FlowTaskIdentityResolver.resolveUserDisplayName(org, "42", " 已有姓名 "));
        assertEquals("张三", FlowTaskIdentityResolver.resolveUserDisplayName(org, "42", null));
    }

    @Test
    void processKeyUsesDefinitionPrefixAndFallsBackForUuid() {
        assertEquals("order_flow", FlowTaskIdentityResolver.extractProcessKey(null, "order_flow:3:123"));
        assertEquals("uuid-123", FlowTaskIdentityResolver.extractProcessKey(null, "uuid-123"));
        assertNull(FlowTaskIdentityResolver.extractProcessKey(null, null));
    }
}
