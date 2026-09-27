package com.mdframe.forge.plugin.capability.secureaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityCatalogService;
import com.mdframe.forge.plugin.capability.execution.GovernedCapabilityExecutionAdapter;
import com.mdframe.forge.plugin.capability.secureaction.gateway.BusinessActionOpenGatewayAdapter;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionPublishedModelPolicy;
import com.mdframe.forge.plugin.capability.secureaction.publish.SecureActionStepValidator;
import com.mdframe.forge.plugin.capability.secureaction.source.BusinessActionSourceService;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionCatalogMapper;
import com.mdframe.forge.plugin.capability.secureaction.catalog.SecureActionCatalogService;
import com.mdframe.forge.plugin.capability.secureaction.mcp.SecureActionMcpHandler;
import com.mdframe.forge.plugin.capability.secureaction.mcp.SecureActionMcpToolContributor;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessActionExecutionService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectActionService;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityDefinition;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceDefinitionRegistry;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceOpenGatewayAdapter;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.system.RestEndpointSystemService;
import com.mdframe.forge.plugin.capability.secureaction.system.LowcodeFormSystemService;
import com.mdframe.forge.plugin.capability.secureaction.system.LowcodeFormInvocationGuard;
import com.mdframe.forge.plugin.capability.secureaction.system.ApplicationProcessCapabilitySource;
import com.mdframe.forge.plugin.capability.secureaction.system.ApplicationProcessStartSystemService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationRuntimeService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationVersionService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.capability.secureaction.mapper.LowcodeFormReceiptMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.manager.DynamicCrudCreateManager;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessObjectService;
import com.mdframe.forge.plugin.generator.mapper.BusinessDocumentConfigMapper;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 已发布业务动作的 MCP 受控写入组合配置。
 */
@AutoConfiguration
public class SecureActionAutoConfiguration {

    @Bean
    public ApplicationProcessCapabilitySource applicationProcessCapabilitySource(
            BusinessApplicationRuntimeService runtime, BusinessApplicationVersionService applicationVersions,
            BusinessObjectMapper objects, BusinessProcessMapper processes,
            BusinessProcessVersionMapper versions, ObjectMapper mapper) {
        return new ApplicationProcessCapabilitySource(
                runtime, applicationVersions, objects, processes, versions, mapper);
    }

    @Bean
    public ApplicationProcessStartSystemService applicationProcessStartSystemService(
            ApplicationProcessCapabilitySource sources, BusinessProcessOrchestrator orchestrator,
            DynamicCrudService records, ObjectMapper mapper, CapabilitySchemaValidator validator) {
        return new ApplicationProcessStartSystemService(
                sources, orchestrator, records, mapper, validator);
    }

    @Bean
    public RestEndpointSystemService restEndpointSystemService(
            ObjectProvider<RequestMappingHandlerMapping> mappings, ObjectMapper mapper, Validator validator) {
        return new RestEndpointSystemService(mappings, mapper, validator);
    }

    @Bean
    public LowcodeFormInvocationGuard lowcodeFormInvocationGuard(
            LowcodeFormReceiptMapper receipts, PlatformTransactionManager transactionManager) {
        return new LowcodeFormInvocationGuard(receipts, transactionManager);
    }

    @Bean
    public LowcodeFormSystemService lowcodeFormSystemService(
            BusinessObjectService objects, BusinessObjectActionService actions, AiCrudConfigService configs,
            DynamicCrudCreateManager formCreate, LowcodeFormInvocationGuard invocations,
            LowcodeRuntimeDataSourceResolver datasourceResolver,
            ObjectMapper mapper, CapabilitySchemaValidator validator,
            BusinessDocumentConfigMapper documents) {
        return new LowcodeFormSystemService(objects, actions, configs, formCreate, invocations, datasourceResolver, mapper, validator, documents);
    }

    @Bean
    public SystemServiceDefinitionRegistry systemServiceDefinitionRegistry(
            List<SystemServiceCapabilityDefinition> definitions) {
        return new SystemServiceDefinitionRegistry(definitions);
    }

    @Bean
    public SystemServiceOpenGatewayAdapter systemServiceOpenGatewayAdapter(
            SystemServiceDefinitionRegistry registry) {
        return new SystemServiceOpenGatewayAdapter(registry);
    }

    @Bean
    public SystemServiceCapabilityPublisher systemServiceCapabilityPublisher(
            SystemServiceDefinitionRegistry registry,
            CapabilityCatalogService catalogService,
            ObjectMapper objectMapper) {
        return new SystemServiceCapabilityPublisher(registry, catalogService, objectMapper);
    }

    @Bean
    public SecureActionStepValidator secureActionStepValidator() {
        return new SecureActionStepValidator();
    }

    @Bean
    public SecureActionPublishedModelPolicy secureActionPublishedModelPolicy(ObjectMapper objectMapper) {
        return new SecureActionPublishedModelPolicy(objectMapper);
    }

    @Bean
    public BusinessActionOpenGatewayAdapter businessActionOpenGatewayAdapter(
            BusinessObjectActionService actionService,
            BusinessActionExecutionService executionService,
            SecureActionStepValidator stepValidator,
            SecureActionPublishedModelPolicy publishedModelPolicy,
            ObjectMapper objectMapper) {
        return new BusinessActionOpenGatewayAdapter(
                actionService, executionService, stepValidator, publishedModelPolicy, objectMapper);
    }

    @Bean
    public BusinessActionCapabilityPublisher businessActionCapabilityPublisher(
            BusinessObjectActionService actionService,
            CapabilityCatalogService catalogService,
            SecureActionStepValidator stepValidator,
            SecureActionPublishedModelPolicy publishedModelPolicy,
            ObjectMapper objectMapper) {
        return new BusinessActionCapabilityPublisher(
                actionService, catalogService, stepValidator, publishedModelPolicy, objectMapper);
    }

    @Bean
    public BusinessActionSourceService businessActionSourceService(
            BusinessObjectActionService actionService,
            SecureActionStepValidator stepValidator,
            SecureActionPublishedModelPolicy publishedModelPolicy) {
        return new BusinessActionSourceService(actionService, stepValidator, publishedModelPolicy);
    }

    /**
     * Runtime MCP exposure remains controlled by the feature switch. Capability publishing is an
     * authenticated control-plane operation and must not disappear when runtime exposure is off.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "forge.capability.secure-actions",
            name = "enabled",
            havingValue = "true")
    static class RuntimeConfiguration {

        @Bean
        SecureActionCatalogService secureActionCatalogService(
                SecureActionCatalogMapper catalogMapper,
                ObjectMapper objectMapper) {
            return new SecureActionCatalogService(catalogMapper, objectMapper);
        }

        @Bean
        SecureActionMcpHandler secureActionMcpHandler(
                SecureActionCatalogService catalogService,
                BusinessObjectActionService actionService,
                BusinessActionExecutionService executionService,
                SecureActionStepValidator stepValidator,
                SecureActionPublishedModelPolicy publishedModelPolicy,
                CapabilitySchemaValidator schemaValidator,
                CapabilityInvocationAuditService auditService,
                ObjectMapper objectMapper,
                List<GovernedCapabilityExecutionAdapter> executionAdapters) {
            return new SecureActionMcpHandler(
                    catalogService, actionService, executionService, stepValidator, publishedModelPolicy,
                    schemaValidator, auditService, objectMapper, executionAdapters);
        }

        @Bean
        SecureActionMcpToolContributor secureActionMcpToolContributor(
                SecureActionMcpHandler handler) {
            return new SecureActionMcpToolContributor(handler);
        }
    }
}
