package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowBindingResolverTest {

    private BusinessBindingMapper mapper;
    private BusinessFlowBindingResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessBindingMapper.class);
        resolver = new BusinessFlowBindingResolver(mapper);
    }

    @Test
    void enabledFlowBindingWinsWithoutLegacyLookup() {
        AiBusinessBinding flow = binding("FLOW", EnableStatus.ENABLED.getCode());
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "FLOW"))
                .thenReturn(flow);

        assertSame(flow, resolver.selectForConfig(1L, "purchase"));
        verify(mapper, never()).selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "APPROVAL");
    }

    @Test
    void enabledLegacyApprovalReplacesDisabledFlowBinding() {
        AiBusinessBinding disabledFlow = binding("FLOW", EnableStatus.DISABLED.getCode());
        AiBusinessBinding legacy = binding("APPROVAL", EnableStatus.ENABLED.getCode());
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "FLOW"))
                .thenReturn(disabledFlow);
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "APPROVAL"))
                .thenReturn(legacy);

        assertSame(legacy, resolver.selectForStart(1L, "purchase"));
    }

    @Test
    void enabledFallbackObjectBindingWinsOverEarlierDisabledBinding() {
        AiBusinessBinding disabled = binding("FLOW", EnableStatus.DISABLED.getCode());
        AiBusinessBinding fallback = binding("FLOW", EnableStatus.ENABLED.getCode());
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "legacy", "FLOW"))
                .thenReturn(disabled);
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "canonical", "FLOW"))
                .thenReturn(fallback);

        assertSame(fallback, resolver.selectForConfig(1L, "legacy", "canonical", "legacy", " "));
    }

    @Test
    void configurationCanInspectFirstDisabledBindingButStartRejectsIt() {
        AiBusinessBinding disabled = binding("FLOW", EnableStatus.DISABLED.getCode());
        when(mapper.selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "FLOW"))
                .thenReturn(disabled);

        assertSame(disabled, resolver.selectForConfig(1L, "purchase"));
        assertNull(resolver.selectForStart(1L, "purchase"));
    }

    private AiBusinessBinding binding(String type, Integer status) {
        AiBusinessBinding binding = new AiBusinessBinding();
        binding.setBindingType(type);
        binding.setStatus(status);
        return binding;
    }
}
