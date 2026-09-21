package com.mdframe.forge.plugin.capability.secureaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityCatalogService;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionCatalogMapper;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionCatalogService;
import com.mdframe.forge.plugin.capability.secureaction.mcp.SecureActionMcpHandler;
import com.mdframe.forge.plugin.capability.secureaction.mcp.SecureActionMcpToolContributor;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityController;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionPublishedModelPolicy;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionStepValidator;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessActionExecutionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.mapper.BusinessDocumentConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.capability.secureaction.mapper.LowcodeFormReceiptMapper;
import com.mdframe.forge.plugin.capability.secureaction.system.LowcodeFormSystemService;
import com.mdframe.forge.plugin.capability.secureaction.system.RestEndpointSystemService;
import jakarta.validation.Validator;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecureActionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecureActionAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Validator.class, () -> mock(Validator.class))
            .withBean(BusinessObjectService.class, () -> mock(BusinessObjectService.class))
            .withBean(BusinessEventPublisher.class, () -> mock(BusinessEventPublisher.class))
            .withBean(AiCrudConfigService.class, () -> mock(AiCrudConfigService.class))
            .withBean(DynamicCrudService.class, () -> mock(DynamicCrudService.class))
            .withBean(BusinessDocumentConfigMapper.class, () -> mock(BusinessDocumentConfigMapper.class))
            .withBean(BusinessObjectMapper.class, () -> mock(BusinessObjectMapper.class))
            .withBean(AiCrudConfigMapper.class, () -> mock(AiCrudConfigMapper.class))
            .withBean(LowcodeFormReceiptMapper.class, () -> mock(LowcodeFormReceiptMapper.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(BusinessObjectActionService.class, () -> mock(BusinessObjectActionService.class))
            .withBean(BusinessActionExecutionService.class, () -> mock(BusinessActionExecutionService.class))
            .withBean(CapabilityCatalogService.class, () -> mock(CapabilityCatalogService.class))
            .withBean(CapabilityInvocationAuditService.class,
                    () -> mock(CapabilityInvocationAuditService.class))
            .withBean(CapabilitySchemaValidator.class, () -> mock(CapabilitySchemaValidator.class))
            .withBean(SecureActionCatalogMapper.class, () -> mock(SecureActionCatalogMapper.class));

    @Test
    void shouldKeepControlPlaneAvailableWhenRuntimeExposureIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LowcodeFormSystemService.class);
            assertThat(context).hasSingleBean(RestEndpointSystemService.class);
            assertThat(context).hasSingleBean(SecureActionStepValidator.class);
            assertThat(context).hasSingleBean(SecureActionPublishedModelPolicy.class);
            assertThat(context).hasSingleBean(BusinessActionCapabilityPublisher.class);
            assertThat(context).doesNotHaveBean(SecureActionCatalogService.class);
            assertThat(context).doesNotHaveBean(SecureActionMcpHandler.class);
            assertThat(context).doesNotHaveBean(SecureActionMcpToolContributor.class);
        });
    }

    @Test
    void shouldEnableRuntimeBeansOnlyWhenSecureActionsAreEnabled() {
        contextRunner
                .withPropertyValues("forge.capability.secure-actions.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecureActionStepValidator.class);
                    assertThat(context).hasSingleBean(BusinessActionCapabilityPublisher.class);
                    assertThat(context).hasSingleBean(SecureActionCatalogService.class);
                    assertThat(context).hasSingleBean(SecureActionMcpHandler.class);
                    assertThat(context).hasSingleBean(SecureActionMcpToolContributor.class);
                });
    }

    @Test
    void shouldNotConditionControlPlaneControllerOnRuntimeSwitch() {
        assertThat(BusinessActionCapabilityController.class
                .getAnnotation(ConditionalOnProperty.class)).isNull();
    }
}
