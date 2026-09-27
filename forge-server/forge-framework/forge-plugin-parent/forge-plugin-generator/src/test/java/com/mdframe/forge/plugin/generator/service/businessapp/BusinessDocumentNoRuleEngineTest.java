package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessDocumentNoRulePreviewDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentNoRulePreviewVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.id.service.ISequenceService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessDocumentNoRuleEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-27T12:34:56Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldInterpretLegacyDateSequenceContextAndFieldTokens() {
        ISequenceService sequenceService = mock(ISequenceService.class);
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(sequenceService, FIXED_CLOCK);
        BusinessDocumentNoRulePreviewDTO dto = new BusinessDocumentNoRulePreviewDTO();
        dto.setTemplate("DOC-{yyyyMMdd}-{seq4}-${suiteCode}-${field:customer_code}");
        dto.setSuiteCode("CRM");
        dto.setSequence(23);
        dto.setSampleData(Map.of("customerCode", "ACME"));

        BusinessDocumentNoRulePreviewVO result = engine.preview(dto);

        assertEquals("DOC-${yyyyMMdd}-${seq:4}-${suiteCode}-${field:customer_code}", result.getTemplate());
        assertEquals("DOC-20260927-0023-CRM-ACME", result.getPreviewNo());
        assertTrue(result.getValid());
        assertTrue(result.getErrors().isEmpty());
        verify(sequenceService, never()).nextId(startsWith("lowcode:document-no:"));
    }

    @Test
    void shouldReportUnknownTokenAndRejectInvalidTemplate() {
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(mock(ISequenceService.class), FIXED_CLOCK);
        BusinessDocumentNoRulePreviewDTO dto = new BusinessDocumentNoRulePreviewDTO();
        dto.setTemplate("DOC-${unknown}");

        BusinessDocumentNoRulePreviewVO result = engine.preview(dto);

        assertFalse(result.getValid());
        assertEquals("未知编号变量: ${unknown}", result.getErrors().get(0).getMessage());
        assertThrows(BusinessException.class, () -> engine.validateTemplate(dto.getTemplate()));
    }

    @Test
    void shouldWarnWhenSampleFieldIsMissingWithoutInvalidatingRule() {
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(mock(ISequenceService.class), FIXED_CLOCK);
        BusinessDocumentNoRulePreviewDTO dto = new BusinessDocumentNoRulePreviewDTO();
        dto.setTemplate("DOC-${field:customerCode}");

        BusinessDocumentNoRulePreviewVO result = engine.preview(dto);

        assertTrue(result.getValid());
        assertEquals("DOC-customerCode", result.getPreviewNo());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    void shouldGenerateRuntimeNumberAndConsumeOneScopedSequence() {
        ISequenceService sequenceService = mock(ISequenceService.class);
        when(sequenceService.nextId(startsWith("lowcode:document-no:9:CRM:ORDER:20260927:"))).thenReturn(7L);
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(sequenceService, FIXED_CLOCK);
        AiBusinessDocumentConfig config = new AiBusinessDocumentConfig();
        config.setTenantId(9L);
        config.setSuiteCode("CRM");
        config.setObjectCode("ORDER");
        config.setDocumentEnabled(EnableStatus.ENABLED.getCode());
        config.setDocumentNoRule("${suiteCode}-${objectCode}-${yyyyMMdd}-${seq:4}-${field:customerNo}");

        String result = engine.generate(config, Map.of("customer_no", "A100"));

        assertEquals("CRM-ORDER-20260927-0007-A100", result);
        verify(sequenceService).nextId(startsWith("lowcode:document-no:9:CRM:ORDER:20260927:"));
    }

    @Test
    void shouldSkipDisabledDocumentWithoutConsumingSequence() {
        ISequenceService sequenceService = mock(ISequenceService.class);
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(sequenceService, FIXED_CLOCK);
        AiBusinessDocumentConfig config = new AiBusinessDocumentConfig();
        config.setDocumentEnabled(EnableStatus.DISABLED.getCode());
        config.setDocumentNoRule("DOC-${seq:4}");

        assertNull(engine.generate(config, Map.of()));
        verify(sequenceService, never()).nextId(startsWith("lowcode:document-no:"));
    }
}
