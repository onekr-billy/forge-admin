package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiDomainDraftDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LowcodeAiDomainPlanningStrategyTest {

    private final LowcodeDomainService domainService = mock(LowcodeDomainService.class);
    private final LowcodeAiDomainPlanningStrategy strategy =
        new LowcodeAiDomainPlanningStrategy(domainService);

    @Test
    void explicitSingleDomainPreferenceCollapsesCrossDomainObjects() {
        List<LowcodeAiObjectPlan> plans = strategy.inferObjectPlans(
            "客户和采购订单放在一个业务领域，不要拆分", null);

        assertTrue(plans.size() >= 2);
        assertEquals(1, plans.stream().map(LowcodeAiObjectPlan::domainCode).distinct().count());
    }

    @Test
    void requestedTablePrefixUpdatesDraftSchemaAndDecision() {
        LowcodeAiDomainDraftDTO domain = strategy.defaultDomainDraft("客户管理");
        LowcodeAiAppGenerateResult result = new LowcodeAiAppGenerateResult();
        result.setDomains(new ArrayList<>(List.of(domain)));

        strategy.applyRequestedTablePrefix(result, "数据表统一以 acme_ 开头");

        assertEquals("acme_", domain.getTablePrefix());
        assertEquals("acme_", domain.getDomainSchema().getNaming().getTablePrefix());
        assertEquals("acme_", result.getDecisions().get(0).getValue());
    }

    @Test
    void disabledExistingDomainUsesAvailableAiSuffix() {
        AiLowcodeDomain disabled = new AiLowcodeDomain();
        disabled.setDomainCode("crm");
        disabled.setStatus("DISABLED");
        when(domainService.getByCode("crm")).thenReturn(disabled);
        when(domainService.getByCode("crm_ai")).thenReturn(null);

        List<LowcodeAiDomainDraftDTO> domains = strategy.buildDomainDrafts(
            List.of(new LowcodeAiObjectPlan("customer", "客户", "crm", false)),
            null,
            "客户管理");

        assertEquals("crm_ai", domains.get(0).getDomainCode());
        assertEquals("biz_crm_ai_", domains.get(0).getTablePrefix());
    }
}
