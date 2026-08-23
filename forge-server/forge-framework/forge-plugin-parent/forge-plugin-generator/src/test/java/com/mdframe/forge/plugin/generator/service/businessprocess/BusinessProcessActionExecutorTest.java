package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessProcessActionExecutor")
class BusinessProcessActionExecutorTest {

    @Test
    @DisplayName("update-record writes the configured constant to the current subject record")
    void updatesCurrentRecordWithConfiguredConstant() {
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessProcessActionExecutor executor = new BusinessProcessActionExecutor(
                configMapper, dynamicCrudService);
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("order_runtime");
        when(configMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order")).thenReturn(config);

        AiBusinessProcessRun run = new AiBusinessProcessRun();
        run.setTenantId(1L);
        run.setSubjectObjectCode("order");
        run.setSubjectRecordId("9001");
        BusinessProcessSchema schema = new BusinessProcessSchema();
        BusinessProcessSchema.Subject subject = new BusinessProcessSchema.Subject();
        subject.setObjectCode("order");
        schema.setSubject(subject);
        BusinessProcessNode node = new BusinessProcessNode();
        node.setType("ACTION");
        node.setConfig(Map.of(
                "actionType", "UPDATE_RECORD",
                "objectCode", "order",
                "fieldMappings", List.of(Map.of(
                        "field", "score",
                        "valueSource", "CONSTANT",
                        "value", "2222"))));

        executor.execute(run, schema, node);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(dynamicCrudService).updateFieldsInternal(
                org.mockito.ArgumentMatchers.eq("order_runtime"),
                org.mockito.ArgumentMatchers.eq("9001"),
                data.capture());
        assertEquals("2222", data.getValue().get("score"));
    }

    @Test
    @DisplayName("generic legacy action object falls back to published process subject")
    void genericObjectFallsBackToSubjectObject() {
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessProcessActionExecutor executor = new BusinessProcessActionExecutor(configMapper, dynamicCrudService);
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("presale_runtime");
        when(configMapper.selectPublishedByObjectCodeOrConfigKey(1L, "presale_registration_business_object"))
                .thenReturn(config);
        AiBusinessProcessRun run = new AiBusinessProcessRun();
        run.setTenantId(1L);
        run.setSubjectObjectCode("business_object");
        run.setSubjectRecordId("9001");
        BusinessProcessSchema schema = new BusinessProcessSchema();
        BusinessProcessSchema.Subject subject = new BusinessProcessSchema.Subject();
        subject.setObjectCode("presale_registration_business_object");
        schema.setSubject(subject);
        BusinessProcessNode node = new BusinessProcessNode();
        node.setConfig(Map.of("actionType", "UPDATE_RECORD", "objectCode", "business_object",
                "fieldMappings", List.of(Map.of("field", "status", "value", "APPROVED"))));

        executor.execute(run, schema, node);

        verify(configMapper).selectPublishedByObjectCodeOrConfigKey(1L, "presale_registration_business_object");
    }
}
