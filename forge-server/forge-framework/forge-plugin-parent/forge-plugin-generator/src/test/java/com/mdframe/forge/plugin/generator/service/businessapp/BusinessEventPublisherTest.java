package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("动态 CRUD 业务事件发布")
class BusinessEventPublisherTest {

    @Test
    @DisplayName("绑定业务对象在写入前校验本地主数据源事务能力")
    void boundObjectRequiresLocalTransactionDatasource() {
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper businessObjectMapper = mock(BusinessObjectMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessEventOutboxService outboxService = mock(BusinessEventOutboxService.class);
        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setObjectCode("purchase_order");
        when(businessObjectMapper.selectByConfigKey(7L, "purchase-order"))
                .thenReturn(businessObject);
        BusinessEventPublisher publisher = new BusinessEventPublisher(
                crudConfigMapper, businessObjectMapper, dynamicCrudService, outboxService);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);
            publisher.assertTransactionalPublishSupported("purchase-order");
        }

        verify(dynamicCrudService).assertLocalTransactionConfig("purchase-order");
    }

    @Test
    @DisplayName("新增事件使用业务对象规范编码启动已发布流程")
    void recordCreatedUsesCanonicalBusinessObjectCode() {
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper businessObjectMapper = mock(BusinessObjectMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessEventOutboxService outboxService = mock(BusinessEventOutboxService.class);

        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setSuiteCode("PRESALE_REGISTRATION");
        businessObject.setObjectCode("business_object");
        when(businessObjectMapper.selectByConfigKey(1L, "presale_registration_business_object"))
                .thenReturn(businessObject);
        when(dynamicCrudService.resolveRecordId(
                "presale_registration_business_object", Map.of("id", 18L)))
                .thenReturn(18L);
        BusinessEventPublisher publisher = new BusinessEventPublisher(
                crudConfigMapper,
                businessObjectMapper,
                dynamicCrudService,
                outboxService);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(1L);
            session.when(SessionHelper::getUsername).thenReturn("admin");

            publisher.publishRecordCreated(
                    "presale_registration_business_object", Map.of("id", 18L));
        }

        ArgumentCaptor<BusinessEvent> eventCaptor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(outboxService, times(2)).append(eventCaptor.capture());
        List<BusinessEvent> events = eventCaptor.getAllValues();
        assertEquals(List.of(BusinessEvent.RECORD_CREATED, BusinessEvent.FORM_SUBMITTED),
                events.stream().map(BusinessEvent::getEventType).toList());
        events.forEach(event -> {
            assertEquals("PRESALE_REGISTRATION", event.getSuiteCode());
            assertEquals("business_object", event.getObjectCode());
            assertEquals("presale_registration_business_object", event.getConfigKey());
            assertEquals("18", event.getRecordId());
            assertEquals(1L, event.getTenantId());
            assertEquals(BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD, event.getEventSource());
            assertEquals(BusinessEventEnvelope.CURRENT_VERSION, event.getEventVersion());
            assertTrue(BusinessEventEnvelope.isTrusted(event));
        });
        assertNotEquals(events.get(0).getEventId(), events.get(1).getEventId());
        verify(crudConfigMapper, org.mockito.Mockito.never())
                .selectByConfigKey(1L, "presale_registration_business_object");
    }
}
