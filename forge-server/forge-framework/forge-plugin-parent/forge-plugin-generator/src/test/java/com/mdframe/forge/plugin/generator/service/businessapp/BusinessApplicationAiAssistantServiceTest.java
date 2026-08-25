package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationAiAssistantChatDTO;
import com.mdframe.forge.plugin.generator.service.AiClientAdapter;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAiAssistantReplyVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationAiAssistantService")
class BusinessApplicationAiAssistantServiceTest {

    @Test
    @DisplayName("chat invokes the bound agent only for a page visible in the filtered published runtime")
    void chatUsesAccessiblePublishedPage() throws Exception {
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        AiClientAdapter aiClient = mock(AiClientAdapter.class);
        when(runtimeService.runtimeByCodeOrSlug("crm-portal")).thenReturn(runtime(
                List.of(page("page_customer")), List.of("page_customer"), List.of("query")));
        when(aiClient.call(eq("crm_helper"), any(), anyMap(), anyInt()))
                .thenReturn(AiClientAdapter.AiClientResult.success("可以按客户名称筛选。"));
        BusinessApplicationAiAssistantService service = new BusinessApplicationAiAssistantService(
                mock(BusinessApplicationService.class), runtimeService, aiClient, new ObjectMapper());

        BusinessApplicationAiAssistantReplyVO reply = service.chat("crm-portal",
                request("page_customer", "query", "怎么查客户？"));

        assertEquals("page_customer", reply.getPageId());
        assertEquals("query", reply.getCapability());
        assertEquals("可以按客户名称筛选。", reply.getContent());
        verify(aiClient).call(eq("crm_helper"), any(), anyMap(), eq(60));
    }

    @Test
    @DisplayName("chat rejects configured pages removed by current user permission filtering")
    void chatRejectsPageOutsideCurrentUserRuntime() throws Exception {
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        AiClientAdapter aiClient = mock(AiClientAdapter.class);
        when(runtimeService.runtimeByCodeOrSlug("crm-portal")).thenReturn(runtime(
                List.of(page("page_customer")), List.of("page_customer", "page_finance"), List.of("query")));
        BusinessApplicationAiAssistantService service = new BusinessApplicationAiAssistantService(
                mock(BusinessApplicationService.class), runtimeService, aiClient, new ObjectMapper());

        BusinessException error = assertThrows(BusinessException.class, () -> service.chat("crm-portal",
                request("page_finance", "query", "查看财务数据")));

        assertTrue(error.getMessage().contains("无权"));
        verify(aiClient, never()).call(any(), any(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("chat rejects capabilities not present in the published assistant binding")
    void chatRejectsUnconfiguredCapability() throws Exception {
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        AiClientAdapter aiClient = mock(AiClientAdapter.class);
        when(runtimeService.runtimeByCodeOrSlug("crm-portal")).thenReturn(runtime(
                List.of(page("page_customer")), List.of("page_customer"), List.of("query")));
        BusinessApplicationAiAssistantService service = new BusinessApplicationAiAssistantService(
                mock(BusinessApplicationService.class), runtimeService, aiClient, new ObjectMapper());

        BusinessException error = assertThrows(BusinessException.class, () -> service.chat("crm-portal",
                request("page_customer", "form", "帮我填表")));

        assertTrue(error.getMessage().contains("未开放"));
        verify(aiClient, never()).call(any(), any(), anyMap(), anyInt());
    }

    private BusinessApplicationRuntimeVO runtime(
            List<Map<String, Object>> visibleNodes,
            List<String> configuredPageIds,
            List<String> capabilities) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(10L);
        application.setApplicationName("客户中心");
        application.setApplicationCode("crm_app");
        application.setStatus(1);
        application.setLastPublishVersion(3);
        application.setAiAssistantConfig(objectMapper.writeValueAsString(Map.of(
                "enabled", true,
                "agentId", "9",
                "agentCode", "crm_helper",
                "pageIds", configuredPageIds,
                "capabilities", capabilities)));
        application.setOptions(objectMapper.writeValueAsString(Map.of(
                "inAppBuilder", Map.of(
                        "nodes", visibleNodes,
                        "pages", Map.of("page_customer", Map.of("title", "客户列表"))))));
        BusinessApplicationRuntimeVO runtime = new BusinessApplicationRuntimeVO();
        runtime.setApplication(application);
        runtime.setVersionNo(3);
        return runtime;
    }

    private Map<String, Object> page(String id) {
        return Map.of("id", id, "type", "page", "title", id);
    }

    private BusinessApplicationAiAssistantChatDTO request(String pageId, String capability, String message) {
        BusinessApplicationAiAssistantChatDTO request = new BusinessApplicationAiAssistantChatDTO();
        request.setPageId(pageId);
        request.setCapability(capability);
        request.setMessage(message);
        return request;
    }
}
