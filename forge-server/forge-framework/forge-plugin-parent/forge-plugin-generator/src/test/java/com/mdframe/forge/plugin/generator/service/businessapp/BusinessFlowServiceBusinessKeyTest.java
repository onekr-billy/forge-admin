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

import java.lang.reflect.Field;

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
    private BusinessFlowRuntimeContextResolver runtimeContextResolver;

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
        runtimeContextResolver = runtimeContextResolver(service);
    }

    @Test
    @DisplayName("FLOW_TEST task key does not mismatch a document businessKey")
    void syntheticTaskKeyAllowsDocumentKey() {
        assertDoesNotThrow(() -> BusinessFlowTaskAccessPolicy.assertBusinessKeyMatches(
                "leave:1001", "FLOW_TEST:leave_flow:1710000000000"));
        assertDoesNotThrow(() -> BusinessFlowTaskAccessPolicy.assertBusinessKeyMatches(
                "FLOW_TEST:leave_flow:1710000000000", "FLOW_TEST:leave_flow:1710000000000"));
    }

    @Test
    @DisplayName("real document keys still have to match")
    void realDocumentKeysMustMatch() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessFlowTaskAccessPolicy.assertBusinessKeyMatches("leave:1001", "leave:1002"));
        assertEquals("业务Key与当前任务不匹配", error.getMessage());
    }

    @Test
    @DisplayName("FLOW_TEST is not parsed as objectCode:recordId")
    void syntheticKeyIsNotADocumentKey() {
        String testKey = "FLOW_TEST:leave_flow:1710000000000";
        assertTrue(BusinessFlowTaskAccessPolicy.isSyntheticTestBusinessKey(testKey));
        assertNull(BusinessFlowIdentityCodec.parseBusinessKeyObjectCode(testKey));
        assertNull(BusinessFlowIdentityCodec.parseBusinessKeyRecordId(testKey));
        assertEquals("leave", BusinessFlowIdentityCodec.parseBusinessKeyObjectCode("leave:1001"));
        assertEquals(1001L, BusinessFlowIdentityCodec.parseBusinessKeyRecordId("leave:1001"));
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

        runtimeContextResolver = runtimeContextResolver(service);
        AiBusinessObject resolved = runtimeContextResolver.resolveTaskBusinessObject(1L, query, link);
        assertEquals("测试", resolved.getObjectName());
        assertEquals("presale_registration_business_object", resolved.getConfigKey());
    }

    private BusinessFlowRuntimeContextResolver runtimeContextResolver(BusinessFlowService target) throws Exception {
        Field field = BusinessFlowService.class.getDeclaredField("businessRuntimeContextResolver");
        field.setAccessible(true);
        return (BusinessFlowRuntimeContextResolver) field.get(target);
    }
}
