package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayItem;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowListDisplayEnricherTest {

    @Test
    void lowcodeItemsShareBatchLoadsAndPreserveStartDisplayFields() {
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessFlowRuntimeContextResolver runtimeResolver = mock(BusinessFlowRuntimeContextResolver.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessCodeFormProviderRegistry providerRegistry = mock(BusinessCodeFormProviderRegistry.class);

        AiBusinessFlowInstanceLink firstLink = link(42L, "purchase:42", "采购单号");
        AiBusinessFlowInstanceLink secondLink = link(43L, "purchase:43", "申请人");
        when(linkMapper.selectLatestByBusinessKeys(eq(1L), anySet()))
                .thenReturn(List.of(firstLink, secondLink));
        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("purchase-runtime");
        runtimeConfig.setObjectCode("purchase");
        runtimeConfig.setObjectName("采购申请");
        AiBusinessObject object = new AiBusinessObject();
        object.setObjectCode("purchase");
        object.setConfigKey("purchase-runtime");
        BusinessRuntimeContext context = new BusinessRuntimeContext(
                "purchase-runtime", "purchase", "purchase-runtime", null, runtimeConfig, object);
        when(runtimeResolver.resolve(1L, "purchase-runtime")).thenReturn(context);
        when(dynamicCrudService.selectByIds("purchase-runtime", List.of(42L, 43L))).thenReturn(Map.of(
                42L, Map.of("orderNo", "PO-42", "amount", 100),
                43L, Map.of("orderNo", "PO-43", "amount", 200)));
        AiBusinessBinding binding = new AiBusinessBinding();
        binding.setBindingConfig("{\"flowModelName\":\"采购审批\"}");
        BusinessObjectVO objectVO = new BusinessObjectVO();
        objectVO.setObjectCode("purchase");
        objectVO.setObjectName("采购申请");
        BusinessFlowListDisplayEnricher enricher = new BusinessFlowListDisplayEnricher(
                () -> 1L,
                linkMapper,
                runtimeResolver,
                dynamicCrudService,
                providerRegistry,
                (tenantId, objectCode, configKey) -> objectVO,
                ignored -> objectVO,
                (tenantId, objectCode) -> binding,
                (businessObject, taskRuntime, record) -> String.valueOf(record.get("orderNo")));
        FlowBusinessListDisplayItem first = item("purchase:42");
        FlowBusinessListDisplayItem second = item("purchase:43");

        enricher.enrich(List.of(first, second));

        assertEquals("PO-42", first.getBusinessSummary());
        assertEquals("PO-43", second.getBusinessSummary());
        assertEquals("采购申请", first.getBusinessObjectName());
        assertEquals("采购审批", first.getProcessDefinitionName());
        assertEquals(100, first.getBusinessParams().get("amount"));
        assertEquals("采购单号", ((List<?>) first.getDisplayExtensions().get("fields")).get(0));
        verify(linkMapper).selectLatestByBusinessKeys(eq(1L), eq(Set.of("purchase:42", "purchase:43")));
        verify(dynamicCrudService).selectByIds("purchase-runtime", List.of(42L, 43L));
    }

    private AiBusinessFlowInstanceLink link(Long recordId, String businessKey, String displayField) {
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setObjectCode("purchase");
        link.setRecordId(recordId);
        link.setBusinessKey(businessKey);
        link.setVariablesSnapshot("{\"configKey\":\"purchase-runtime\",\"businessParams\":"
                + "{\"displayFields\":[\"" + displayField + "\"],\"source\":\"start\"}}" );
        return link;
    }

    private FlowBusinessListDisplayItem item(String businessKey) {
        FlowBusinessListDisplayItem item = new FlowBusinessListDisplayItem();
        item.setBusinessKey(businessKey);
        item.setProcessDefKey("purchase-flow");
        return item;
    }
}
