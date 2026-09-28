package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("动态 CRUD 业务事件发布")
class BusinessEventPublisherTest {

    @Test
    @DisplayName("新增事件使用业务对象规范编码启动已发布流程")
    void recordCreatedUsesCanonicalBusinessObjectCode() {
        BusinessTriggerExecutor triggerExecutor = mock(BusinessTriggerExecutor.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper businessObjectMapper = mock(BusinessObjectMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BusinessProcessOrchestrator> orchestratorProvider = mock(ObjectProvider.class);
        BusinessProcessOrchestrator orchestrator = mock(BusinessProcessOrchestrator.class);

        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setSuiteCode("PRESALE_REGISTRATION");
        businessObject.setObjectCode("business_object");
        when(businessObjectMapper.selectByConfigKey(1L, "presale_registration_business_object"))
                .thenReturn(businessObject);
        when(dynamicCrudService.resolveRecordId(
                "presale_registration_business_object", Map.of("id", 18L)))
                .thenReturn(18L);
        when(orchestratorProvider.getIfAvailable()).thenReturn(orchestrator);

        BusinessEventPublisher publisher = new BusinessEventPublisher(
                triggerExecutor,
                crudConfigMapper,
                businessObjectMapper,
                dynamicCrudService,
                orchestratorProvider);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(1L);
            session.when(SessionHelper::getUsername).thenReturn("admin");

            publisher.publishRecordCreated(
                    "presale_registration_business_object", Map.of("id", 18L));
        }

        ArgumentCaptor<BusinessEvent> eventCaptor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(orchestrator, times(2)).startEvent(eventCaptor.capture());
        List<BusinessEvent> events = eventCaptor.getAllValues();
        assertEquals(List.of(BusinessEvent.RECORD_CREATED, BusinessEvent.FORM_SUBMITTED),
                events.stream().map(BusinessEvent::getEventType).toList());
        events.forEach(event -> {
            assertEquals("PRESALE_REGISTRATION", event.getSuiteCode());
            assertEquals("business_object", event.getObjectCode());
            assertEquals("presale_registration_business_object", event.getConfigKey());
            assertEquals("18", event.getRecordId());
            assertEquals(1L, event.getTenantId());
        });
        verify(triggerExecutor, times(2)).executeTriggersAsync(eventCaptor.capture());
        verify(crudConfigMapper, never()).selectByConfigKey(1L, "presale_registration_business_object");
    }
}
