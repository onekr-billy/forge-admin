package com.mdframe.forge.admin.integration;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.admin.integration.controller.ApplicationIntegrationController;
import com.mdframe.forge.admin.integration.service.ApplicationIntegrationService;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityGrantMapper;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityGrant;
import com.mdframe.forge.plugin.capability.controlplane.service.*;
import com.mdframe.forge.plugin.capability.flowaction.publish.FlowActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityPublisher;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationIntegrationControllerTest {
    @Test void explicitEntryCannotBypassDisabledWorkbenchSso() {
        var configs = mock(com.mdframe.forge.starter.social.service.ISocialConfigService.class);
        var apps = mock(com.mdframe.forge.starter.social.service.ISocialAppConfigService.class);
        var providers = mock(com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry.class);
        var oauth = mock(com.mdframe.forge.starter.social.service.SocialOAuthStateService.class);
        var connection = new com.mdframe.forge.starter.social.domain.entity.SysSocialConfig();
        connection.setStatus(1); connection.setSsoWorkbenchEnabled(0);
        when(configs.selectConnectionByCode("wecom")).thenReturn(connection);
        var controller = new com.mdframe.forge.plugin.collaboration.controller.CollaborationLoginController(configs, apps, providers, oauth);
        assertThrows(BusinessException.class, () -> controller.authorize("wecom", "https://example.test/app/legal", "pc"));
        verifyNoInteractions(providers, oauth, apps);
    }
    @Test void everyEndpointRequiresBothApplicationAndPlatformPermissions() {
        Arrays.stream(ApplicationIntegrationController.class.getDeclaredMethods()).filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).forEach(method -> {
            var permission = method.getAnnotation(SaCheckPermission.class);
            assertNotNull(permission, method.getName());
            assertEquals(2, permission.value().length, method.getName());
            assertTrue(permission.value()[0].startsWith("ai:businessApplication:"));
            assertEquals(cn.dev33.satoken.annotation.SaMode.AND, permission.mode());
        });
    }
    @Test void publishAndAssociationShareRollbackBoundary() {
        Arrays.stream(ApplicationIntegrationController.class.getDeclaredMethods())
                .filter(m -> Arrays.asList("flow", "action", "system").contains(m.getName()))
                .forEach(m -> assertNotNull(m.getAnnotation(Transactional.class)));
    }
    @Test void revokeRejectsAnotherApplicationGrantBeforeMutation() {
        var integrations = mock(ApplicationIntegrationService.class);
        var grants = mock(CapabilityGrantService.class);
        var mapper = mock(AiCapabilityGrantMapper.class);
        when(integrations.tenant()).thenReturn(7L);
        AiCapabilityGrant grant = new AiCapabilityGrant(); grant.setCapabilityId(44L);
        when(mapper.selectTenantById(7L, 55L)).thenReturn(grant);
        doThrow(new BusinessException("其他应用")).when(integrations).requireCapability(11L, 44L);
        var controller = new ApplicationIntegrationController(integrations, mock(FlowActionCapabilityPublisher.class),
                mock(BusinessActionCapabilityPublisher.class), mock(SystemServiceCapabilityPublisher.class), grants,
                mapper, mock(CapabilityClientService.class), mock(CapabilityInvocationAuditService.class));
        assertThrows(BusinessException.class, () -> controller.revoke(11L, 55L));
        verifyNoInteractions(grants);
    }
}
