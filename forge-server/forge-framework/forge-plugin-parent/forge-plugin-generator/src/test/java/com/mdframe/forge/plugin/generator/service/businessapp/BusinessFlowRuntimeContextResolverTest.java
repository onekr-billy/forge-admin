package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowRuntimeContextResolverTest {

    private AiCrudConfigMapper crudConfigMapper;
    private BusinessDocumentConfigService documentConfigService;
    private BusinessObjectMapper businessObjectMapper;
    private BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private BusinessFlowRuntimeContextResolver resolver;

    @BeforeEach
    void setUp() {
        crudConfigMapper = mock(AiCrudConfigMapper.class);
        documentConfigService = mock(BusinessDocumentConfigService.class);
        businessObjectMapper = mock(BusinessObjectMapper.class);
        flowInstanceLinkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        resolver = new BusinessFlowRuntimeContextResolver(
                new BusinessRuntimeConfigResolver(crudConfigMapper, () -> 1L),
                documentConfigService,
                businessObjectMapper,
                flowInstanceLinkMapper,
                null,
                null,
                () -> 1L,
                (tenantId, objectCode, fallbackCodes) -> null,
                (key, startedAt) -> { },
                note -> { });
    }

    @Test
    void taskFormSnapshotHydratesStableIdentityBeforeLegacyHints() {
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId("task-1");

        resolver.hydrateTaskFormQuery(query, Map.of(
                "processInstanceId", "process-1",
                "businessKey", "purchase:42",
                "taskDefKey", "approve",
                "formRef", Map.of(
                        "formKey", "purchase-form",
                        "objectCode", "purchase",
                        "objectId", "31",
                        "configKey", "purchase-runtime",
                        "recordId", "42"),
                "variables", Map.of(
                        "objectCode", "legacy-purchase",
                        "configKey", "legacy-runtime")));

        assertEquals("process-1", query.getProcessInstanceId());
        assertEquals("purchase:42", query.getBusinessKey());
        assertEquals("purchase", query.getObjectCode());
        assertEquals(31L, query.getObjectId());
        assertEquals("purchase-runtime", query.getConfigKey());
        assertEquals(42L, query.getRecordId());
        assertEquals("purchase-form", query.getFormKey());
    }

    @Test
    void documentAndObjectMetadataCanonicalizeRuntimeLookup() {
        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setObjectCode("runtime-object");
        runtimeConfig.setConfigKey("runtime-config");
        AiBusinessDocumentConfig documentConfig = new AiBusinessDocumentConfig();
        documentConfig.setObjectCode("canonical-object");
        documentConfig.setConfigKey("document-config");
        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setObjectCode("canonical-object");
        businessObject.setConfigKey("document-config");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "legacy-object"))
                .thenReturn(runtimeConfig);
        when(documentConfigService.selectEnabledByObjectCode(1L, "legacy-object"))
                .thenReturn(documentConfig);
        when(businessObjectMapper.selectByConfigKey(1L, "document-config"))
                .thenReturn(businessObject);

        BusinessRuntimeContext context = resolver.resolve(1L, "legacy-object");

        assertEquals("canonical-object", context.objectCode());
        assertEquals("document-config", context.configKey());
        assertSame(documentConfig, context.documentConfig());
        assertSame(runtimeConfig, context.runtimeConfig());
        assertSame(businessObject, context.businessObject());
    }

    @Test
    void syntheticTaskKeyNeverReusesRequestedRecordId() {
        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setObjectCode("purchase");
        businessObject.setConfigKey("purchase-runtime");
        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setObjectCode("purchase");
        runtimeConfig.setConfigKey("purchase-runtime");
        when(businessObjectMapper.selectByConfigKey(1L, "purchase-runtime"))
                .thenReturn(businessObject);
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "purchase-runtime"))
                .thenReturn(runtimeConfig);
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setBusinessKey("FLOW_TEST:purchase-flow:1710000000000");
        query.setObjectCode("purchase");
        query.setConfigKey("purchase-runtime");
        query.setRecordId(999L);

        TaskFormRuntimeContext context = resolver.resolveTask(query, true, Map.of());

        assertNull(context.recordId());
        assertEquals("purchase", context.objectCode());
        assertEquals("purchase-runtime", context.configKey());
        verify(flowInstanceLinkMapper, never()).selectLatestByBusinessKey(
                1L, "FLOW_TEST:purchase-flow:1710000000000");
    }
}
