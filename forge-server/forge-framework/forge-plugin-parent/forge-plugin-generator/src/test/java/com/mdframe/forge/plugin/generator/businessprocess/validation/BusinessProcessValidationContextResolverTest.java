package com.mdframe.forge.plugin.generator.businessprocess.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessPermissionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessageTemplate;
import com.mdframe.forge.plugin.message.service.MessageTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BusinessProcessValidationContextResolver")
class BusinessProcessValidationContextResolverTest {

    @Test
    @DisplayName("context is built from current application assets and immutable published snapshots")
    void resolvesGovernedApplicationAssets() {
        ObjectMapper objectMapper = new ObjectMapper();
        BusinessApplicationObjectMapper applicationObjectMapper = mock(BusinessApplicationObjectMapper.class);
        BusinessObjectDesignVersionMapper objectVersionMapper = mock(BusinessObjectDesignVersionMapper.class);
        BusinessPermissionMapper permissionMapper = mock(BusinessPermissionMapper.class);
        BusinessProcessVersionMapper processVersionMapper = mock(BusinessProcessVersionMapper.class);
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        FlowClient flowClient = mock(FlowClient.class);
        MessageTemplateService messageTemplateService = mock(MessageTemplateService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FlowClient> flowClientProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MessageTemplateService> templateProvider = mock(ObjectProvider.class);
        BusinessProcessSchemaValidator validator = new BusinessProcessSchemaValidator(objectMapper);
        BusinessProcessValidationContextResolver resolver = new BusinessProcessValidationContextResolver(
                objectMapper,
                applicationObjectMapper,
                objectVersionMapper,
                permissionMapper,
                processVersionMapper,
                flowService,
                flowClientProvider,
                templateProvider,
                validator);
        BusinessApplicationObjectVO object = applicationObject();
        AiBusinessObjectDesignVersion objectVersion = objectVersion();
        AiBusinessProcessVersion childVersion = childVersion();
        when(applicationObjectMapper.selectByApplicationId(1L, 10L)).thenReturn(List.of(object));
        when(objectVersionMapper.selectLatestPublishedVersions(1L, List.of(20L)))
                .thenReturn(List.of(objectVersion));
        when(processVersionMapper.selectCurrentPublishedByApplication(1L, 10L))
                .thenReturn(List.of(childVersion));
        when(flowClientProvider.getIfAvailable()).thenReturn(flowClient);
        List<Map<String, Object>> publishedModels = List.of(Map.of(
                "status", 1,
                "id", "model-100",
                "modelKey", "order_approval",
                "modelName", "订单审批",
                "designerType", "approval",
                "version", 3,
                "processDefinitionId", "order_approval:3:100",
                "deploymentId", "deployment-1"));
        when(flowClient.getModelList(null, 1)).thenReturn(FlowResult.success(publishedModels));
        when(flowClient.getModelList(null, null)).thenReturn(FlowResult.success(publishedModels));
        when(flowService.getFormAssets("order")).thenReturn(Map.of(
                "formAssets", List.of(Map.of("formKey", "order_form"))));
        when(permissionMapper.selectExistingPermissions(
                1L, List.of("ai:businessProcess:start")))
                .thenReturn(List.of("ai:businessProcess:start"));
        when(templateProvider.getIfAvailable()).thenReturn(messageTemplateService);
        SysMessageTemplate template = new SysMessageTemplate();
        template.setTemplateCode("order_notice");
        template.setEnabled(1);
        when(messageTemplateService.getByCode("order_notice")).thenReturn(template);
        BusinessProcessSchema schema = validator.normalize(schemaJson());

        BusinessProcessValidationContext context = resolver.resolve(
                1L, 10L, "order_submit", schema);

        assertEquals("20", context.getObjectIdsByCode().get("order"));
        assertEquals(java.util.Set.of("id", "status"), context.getFieldsByObjectCode().get("order"));
        assertEquals(String.valueOf(objectVersion.getId()),
                context.getPublishedObjectVersionIdsByCode().get("order"));
        assertTrue(context.getAvailableFlowModelKeys().contains("order_approval"));
        assertTrue(context.getAvailableFormAssetKeys().contains("order_form"));
        assertTrue(context.getAvailableBusinessActionCodes().contains("confirm"));
        assertTrue(context.getAvailableMessageTemplateCodes().contains("order_notice"));
        assertTrue(context.getKnownPermissions().contains("ai:businessProcess:start"));
        assertTrue(context.getPublishedSubProcessCodes().contains("child_process"));
        assertTrue(context.getSubProcessDependencies().get("child_process").contains("leaf_process"));
        assertFalse(context.isCapabilityBridgeAvailable());

        var availableModels = resolver.resolveAvailableFlowModels(1L, 10L);
        assertEquals(1, availableModels.size());
        assertEquals("model-100", availableModels.get(0).getModelId());
        assertEquals("order_approval", availableModels.get(0).getModelKey());
        assertEquals("订单审批", availableModels.get(0).getModelName());
        when(flowClient.getModelList(null, 1)).thenReturn(FlowResult.success(List.of(Map.of(
                "status", 0,
                "modelKey", "order_approval",
                "deploymentId", ""))));
        BusinessProcessValidationContext unpublishedContext = resolver.resolve(
                1L, 10L, "order_submit", schema);
        assertFalse(unpublishedContext.getAvailableFlowModelKeys().contains("order_approval"));

        when(flowClient.getModelList(null, 1)).thenReturn(FlowResult.success(List.of(Map.of(
                "status", 1,
                "modelKey", "order_approval",
                "version", 0,
                "processDefinitionId", "order_approval:0:100",
                "deploymentId", "deployment-0"))));
        BusinessProcessValidationContext invalidVersionContext = resolver.resolve(
                1L, 10L, "order_submit", schema);
        assertFalse(invalidVersionContext.getAvailableFlowModelKeys().contains("order_approval"));
    }

    private BusinessApplicationObjectVO applicationObject() {
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(20L);
        object.setObjectCode("order");
        object.setObjectStatus(1);
        object.setModelSchema("{\"fields\":[{\"field\":\"id\"},{\"field\":\"status\"}]}");
        return object;
    }

    private AiBusinessObjectDesignVersion objectVersion() {
        AiBusinessObjectDesignVersion version = new AiBusinessObjectDesignVersion();
        version.setId(501L);
        version.setObjectId(20L);
        version.setDesignerOptionsSnapshot("{\"actions\":[{\"actionCode\":\"confirm\",\"status\":1}]}");
        return version;
    }

    private AiBusinessProcessVersion childVersion() {
        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setProcessCode("child_process");
        version.setSchemaJson("""
                {
                  "schemaVersion":"1.0",
                  "processCode":"child_process",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"EVENT_RECORD"},
                  "nodes":[],"edges":[],
                  "policies":{"approvalConcurrency":"ONE_ACTIVE_PER_BUSINESS_KEY","maxSubProcessDepth":5,"retry":{"mode":"LIMITED","maxAttempts":1,"backoffSeconds":[30]}},
                  "dependencies":{"objects":["order"],"flowModels":[],"formAssets":[],"businessActions":[],"messageTemplates":[],"capabilities":[],"subProcesses":["leaf_process"]}
                }
                """);
        return version;
    }

    private String schemaJson() {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"order_submit",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_MANUAL","name":"提交","config":{"permission":"ai:businessProcess:start"}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[{"id":"e1","source":"start","target":"end","sourcePort":"NEXT"}],
                  "policies":{"approvalConcurrency":"ONE_ACTIVE_PER_BUSINESS_KEY","maxSubProcessDepth":5,"retry":{"mode":"LIMITED","maxAttempts":1,"backoffSeconds":[30]}},
                  "dependencies":{"objects":["order"],"flowModels":["order_approval"],"formAssets":["order_form"],"businessActions":["confirm"],"messageTemplates":["order_notice"],"capabilities":[],"subProcesses":["child_process"]}
                }
                """;
    }
}
