package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("business flow test businessKey identity")
class BusinessFlowServiceBusinessKeyTest {

    private BusinessFlowService service;
    private Method assertBusinessKeyMatches;
    private Method parseBusinessKeyObjectCode;
    private Method parseBusinessKeyRecordId;
    private Method isSyntheticTestBusinessKey;
    private Method resolveTaskBusinessObject;

    @BeforeEach
    void setUp() throws Exception {
        service = new BusinessFlowService(
                mock(BusinessBindingMapper.class),
                mock(BusinessFlowInstanceLinkMapper.class),
                mock(AiCrudConfigMapper.class),
                mock(BusinessObjectMapper.class),
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                mock(DynamicCrudService.class),
                mock(BusinessFieldDesignService.class),
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
        assertBusinessKeyMatches = BusinessFlowService.class.getDeclaredMethod(
                "assertBusinessKeyMatches", String.class, Object.class);
        assertBusinessKeyMatches.setAccessible(true);
        parseBusinessKeyObjectCode = BusinessFlowService.class.getDeclaredMethod(
                "parseBusinessKeyObjectCode", String.class);
        parseBusinessKeyObjectCode.setAccessible(true);
        parseBusinessKeyRecordId = BusinessFlowService.class.getDeclaredMethod(
                "parseBusinessKeyRecordId", String.class);
        parseBusinessKeyRecordId.setAccessible(true);
        isSyntheticTestBusinessKey = BusinessFlowService.class.getDeclaredMethod(
                "isSyntheticTestBusinessKey", String.class);
        isSyntheticTestBusinessKey.setAccessible(true);
        resolveTaskBusinessObject = BusinessFlowService.class.getDeclaredMethod(
                "resolveTaskBusinessObject", Long.class, BusinessTaskFormContextQueryDTO.class,
                AiBusinessFlowInstanceLink.class);
        resolveTaskBusinessObject.setAccessible(true);
    }

    @Test
    @DisplayName("FLOW_TEST task key does not mismatch a document businessKey")
    void syntheticTaskKeyAllowsDocumentKey() {
        assertDoesNotThrow(() -> invokeAssert("leave:1001", "FLOW_TEST:leave_flow:1710000000000"));
        assertDoesNotThrow(() -> invokeAssert("FLOW_TEST:leave_flow:1710000000000", "FLOW_TEST:leave_flow:1710000000000"));
    }

    @Test
    @DisplayName("real document keys still have to match")
    void realDocumentKeysMustMatch() {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> invokeAssert("leave:1001", "leave:1002"));
        assertTrue(error.getCause() instanceof BusinessException);
        assertEquals("业务Key与当前任务不匹配", error.getCause().getMessage());
    }

    @Test
    @DisplayName("FLOW_TEST is not parsed as objectCode:recordId")
    void syntheticKeyIsNotADocumentKey() throws Exception {
        String testKey = "FLOW_TEST:leave_flow:1710000000000";
        assertEquals(Boolean.TRUE, isSyntheticTestBusinessKey.invoke(service, testKey));
        assertNull(parseBusinessKeyObjectCode.invoke(service, testKey));
        assertNull(parseBusinessKeyRecordId.invoke(service, testKey));
        assertEquals("leave", parseBusinessKeyObjectCode.invoke(service, "leave:1001"));
        assertEquals(1001L, parseBusinessKeyRecordId.invoke(service, "leave:1001"));
    }

    @Test
    @DisplayName("task form resolves the object by configKey before duplicate objectCode")
    void taskFormUsesConfigKeyIdentity() throws Exception {
        BusinessObjectMapper mapper = mock(BusinessObjectMapper.class);
        service = new BusinessFlowService(
                mock(BusinessBindingMapper.class),
                mock(BusinessFlowInstanceLinkMapper.class),
                mock(AiCrudConfigMapper.class),
                mapper,
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                mock(DynamicCrudService.class),
                mock(BusinessFieldDesignService.class),
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
        AiBusinessObject expected = new AiBusinessObject();
        expected.setObjectCode("business_object");
        expected.setObjectName("测试");
        expected.setConfigKey("presale_registration_business_object");
        when(mapper.selectByConfigKey(1L, "presale_registration_business_object")).thenReturn(expected);

        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setObjectCode("business_object");
        query.setConfigKey("presale_registration_business_object");
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setObjectCode("business_object");
        link.setVariablesSnapshot("{\"configKey\":\"presale_registration_business_object\"}");

        AiBusinessObject resolved = (AiBusinessObject) resolveTaskBusinessObject.invoke(service, 1L, query, link);
        assertEquals("测试", resolved.getObjectName());
        assertEquals("presale_registration_business_object", resolved.getConfigKey());
    }

    private void invokeAssert(String requested, Object actual) throws Exception {
        assertBusinessKeyMatches.invoke(service, requested, actual);
    }
}
