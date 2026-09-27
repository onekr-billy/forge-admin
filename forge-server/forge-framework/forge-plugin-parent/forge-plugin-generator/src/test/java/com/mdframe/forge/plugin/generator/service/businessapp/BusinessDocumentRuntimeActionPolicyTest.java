package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentRuntimeVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class BusinessDocumentRuntimeActionPolicyTest {

    private final BusinessDocumentRuntimeActionPolicy policy = new BusinessDocumentRuntimeActionPolicy();

    @Test
    void shouldProjectEnabledStartActionForManualDraft() {
        BusinessDocumentRuntimeVO runtime = runtime("DRAFT");
        BusinessDocumentConfigVO config = config("MANUAL", true);
        AiBusinessDocumentConfig documentConfig = documentConfig();

        policy.fillNextAction(runtime, config, null, List.of("START_FLOW"));
        policy.fillRuntimeActions(runtime, documentConfig, config, null, List.of("START_FLOW"));

        assertEquals("START_FLOW", runtime.getNextAction());
        BusinessDocumentRuntimeVO.RuntimeActionVO action = runtime.getRuntimeActions().get(0);
        assertEquals("START_FLOW", action.getKey());
        assertEquals(Long.valueOf(9001L), action.getRecordId());
        assertTrue(Boolean.TRUE.equals(action.getVisible()));
        assertFalse(Boolean.TRUE.equals(action.getDisabled()));
    }

    @Test
    void shouldUseTriggerNextActionAndHideManualStartProjection() {
        BusinessDocumentRuntimeVO runtime = runtime("DRAFT");
        BusinessDocumentConfigVO config = config("TRIGGER", true);

        policy.fillNextAction(runtime, config, null, List.of("START_FLOW"));
        policy.fillRuntimeActions(runtime, documentConfig(), config, null, List.of("START_FLOW"));

        assertEquals("CONFIG_TRIGGER", runtime.getNextAction());
        assertTrue(runtime.getRuntimeActions().isEmpty());
    }

    @Test
    void shouldHideStartProjectionWhenDocumentAlreadyHasFlowInstance() {
        BusinessDocumentRuntimeVO runtime = runtime("APPROVED");
        BusinessDocumentConfigVO config = config("MANUAL", false);
        AiBusinessFlowInstanceLink link = link("APPROVED");

        policy.fillNextAction(runtime, config, link, List.of("START_FLOW"));
        policy.fillRuntimeActions(runtime, documentConfig(), config, link, List.of("START_FLOW"));

        assertEquals("VIEW_FLOW", runtime.getNextAction());
        assertFalse(Boolean.TRUE.equals(runtime.getRuntimeActions().get(0).getVisible()));
    }

    @Test
    void shouldProjectWithdrawOnlyForInitiatorWithEndpointPermission() {
        BusinessDocumentRuntimeVO runtime = runtime("IN_PROCESS");
        AiBusinessFlowInstanceLink link = link("IN_PROCESS");
        List<BusinessDocumentRuntimeVO.RuntimeActionVO> actions = new ArrayList<>();

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getUserId).thenReturn(100L);
            session.when(() -> SessionHelper.hasPermission("ai:businessDocument:withdraw")).thenReturn(true);

            policy.addWithdrawAction(actions, runtime, "order", link, List.of("WITHDRAW"));
        }

        assertEquals(1, actions.size());
        assertEquals("WITHDRAW_FLOW", actions.get(0).getKey());
        assertEquals(Long.valueOf(9001L), actions.get(0).getRecordId());
    }

    private BusinessDocumentRuntimeVO runtime(String status) {
        BusinessDocumentRuntimeVO runtime = new BusinessDocumentRuntimeVO();
        runtime.setBusinessKey("order:9001");
        runtime.setDocumentStatus(status);
        return runtime;
    }

    private AiBusinessDocumentConfig documentConfig() {
        AiBusinessDocumentConfig config = new AiBusinessDocumentConfig();
        config.setObjectCode("order");
        return config;
    }

    private AiBusinessFlowInstanceLink link(String status) {
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setFlowStatus(status);
        link.setProcessInstanceId("flow-instance-1");
        link.setStartUserId(100L);
        return link;
    }

    private BusinessDocumentConfigVO config(String startMode, boolean allowStart) {
        BusinessDocumentConfigVO config = new BusinessDocumentConfigVO();
        config.setMainFlowSummary(new LinkedHashMap<>(Map.of(
                "configured", true,
                "flowModelKey", "order_approval",
                "startMode", startMode)));
        BusinessDocumentConfigVO.StatusMappingRowVO row = new BusinessDocumentConfigVO.StatusMappingRowVO();
        row.setStandardStatus(allowStart ? "DRAFT" : "APPROVED");
        row.setStatusValue(row.getStandardStatus());
        row.setDisplayName(row.getStandardStatus());
        row.setAllowStartFlow(allowStart);
        config.setStatusMappingRows(List.of(row));
        return config;
    }
}
