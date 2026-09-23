package com.mdframe.forge.admin.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.admin.integration.dto.ApplicationCapabilityCandidate;
import com.mdframe.forge.admin.integration.dto.ApplicationIntegrationConfig;
import com.mdframe.forge.admin.integration.mapper.ApplicationIntegrationMapper;
import com.mdframe.forge.admin.integration.service.ApplicationIntegrationService;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessMessageChannel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.BusinessMessageChannelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationRuntimeService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationService;
import com.mdframe.forge.plugin.generator.vo.businessapp.*;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.security.SecretSummary;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ApplicationIntegrationServiceTest {
    ApplicationIntegrationMapper mapper = mock(ApplicationIntegrationMapper.class);
    BusinessApplicationService applications = mock(BusinessApplicationService.class);
    BusinessApplicationRuntimeService runtime = mock(BusinessApplicationRuntimeService.class);
    BusinessObjectMapper objects = mock(BusinessObjectMapper.class);
    AiCapabilityMapper capabilities = mock(AiCapabilityMapper.class);
    BusinessMessageChannelMapper channels = mock(BusinessMessageChannelMapper.class);
    ISocialConfigService connections = mock(ISocialConfigService.class);
    ISocialAppConfigService socialApps = mock(ISocialAppConfigService.class);
    CollaborationProviderRegistry providers = mock(CollaborationProviderRegistry.class);
    ApplicationIntegrationService service = new ApplicationIntegrationService(mapper, applications, runtime,
            objects, capabilities, channels, connections, socialApps, providers, new ObjectMapper());
    MockedStatic<SessionHelper> session;

    @BeforeEach void setup() {
        session = mockStatic(SessionHelper.class);
        session.when(SessionHelper::getTenantId).thenReturn(7L);
        session.when(SessionHelper::getUserId).thenReturn(8L);
        session.when(SessionHelper::getActiveOrgId).thenReturn(9L);
        BusinessApplicationVO app = new BusinessApplicationVO();
        app.setId(11L); app.setApplicationCode("legal"); app.setApplicationName("法律协同");
        when(applications.detail(11L)).thenReturn(app);
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        published.setObjects(List.of());
        when(runtime.runtimeById(11L)).thenReturn(published);
    }
    @AfterEach void close() { session.close(); }

    @Test void missingTenantDoesNotReadOrUseDefault() {
        session.when(SessionHelper::getTenantId).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.config(11L));
        verifyNoInteractions(mapper, applications);
    }
    @Test void rejectsConnectionInOtherTenantBeforeWriting() {
        SysSocialConfig connection = new SysSocialConfig(); connection.setTenantId(88L); connection.setStatus(1);
        when(connections.selectConfigById(22L)).thenReturn(connection);
        ApplicationIntegrationConfig dto = new ApplicationIntegrationConfig(); dto.setConnectionId(22L);
        assertThrows(BusinessException.class, () -> service.save(11L, dto));
        verifyNoInteractions(mapper, channels);
    }
    @Test void staleRevisionDoesNotTouchMessageChannel() {
        when(mapper.save(anyLong(), anyLong(), any(), anyLong())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.save(11L, new ApplicationIntegrationConfig()));
        verifyNoInteractions(channels);
    }
    @Test void unbindingDisablesExistingChannelWithoutDeletingHistory() {
        when(mapper.save(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
        AiBusinessMessageChannel channel = new AiBusinessMessageChannel(); channel.setId(12L);
        when(channels.selectByChannelCode(7L, "app_11_collaboration")).thenReturn(channel);
        service.save(11L, new ApplicationIntegrationConfig());
        verify(channels).updateById(argThat((AiBusinessMessageChannel c) -> c.getStatus() == 0 && c.getChannelConfigRef() == null));
        verify(channels, never()).deleteById(any(java.io.Serializable.class));
    }
    @Test void bindsOnlyReferenceAndCreatesActualMessageChannel() {
        SysSocialConfig connection = new SysSocialConfig(); connection.setId(22L); connection.setTenantId(7L);
        connection.setPlatform("WECHAT_ENTERPRISE"); connection.setStatus(1); connection.setEnterpriseId("corp");
        configuredApp();
        when(connections.selectConfigById(22L)).thenReturn(connection);
        when(providers.supports("WECHAT_ENTERPRISE", CollaborationCapability.MESSAGE)).thenReturn(true);
        when(mapper.save(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
        ApplicationIntegrationConfig dto = new ApplicationIntegrationConfig(); dto.setConnectionId(22L);
        service.save(11L, dto);
        verify(channels).insert(argThat((AiBusinessMessageChannel c) -> c.getTenantId().equals(7L)
                && c.getChannelType().equals("COLLABORATION") && c.getChannelConfigRef().equals("22") && c.getStatus() == 1));
    }
    SysSocialAppConfig configuredApp() {
        SysSocialAppConfig app = new SysSocialAppConfig(); app.setAgentId("100001"); app.setClientId("corp");
        when(socialApps.requireEnabledApp(eq(7L), eq(22L), any())).thenReturn(app);
        when(socialApps.secretSummary(app)).thenReturn(new SecretSummary(true, "******", "ACTIVE", null, null));
        return app;
    }
    SysSocialConfig loginConnection() {
        SysSocialConfig connection = new SysSocialConfig(); connection.setId(22L); connection.setTenantId(7L);
        connection.setPlatform("WECHAT_ENTERPRISE"); connection.setStatus(1); connection.setSsoWorkbenchEnabled(1);
        connection.setEnterpriseId("corp");
        when(connections.selectConfigById(22L)).thenReturn(connection);
        when(connections.selectConfigList(any())).thenReturn(List.of(connection));
        when(providers.supports("WECHAT_ENTERPRISE", CollaborationCapability.LOGIN)).thenReturn(true);
        configuredApp();
        return connection;
    }
    @Test void loginOnlyBindingDoesNotFreezeMessageReadinessAtSaveTime() {
        loginConnection();
        when(mapper.save(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
        ApplicationIntegrationConfig dto = new ApplicationIntegrationConfig(); dto.setConnectionId(22L);
        service.save(11L, dto);
        // The channel represents the binding. Delivery still checks the current MESSAGE app each time.
        verify(channels).insert(argThat((AiBusinessMessageChannel c) -> c.getStatus() == 1));
        assertFalse(service.connections(11L).get(0).messageAvailable());
        when(providers.supports("WECHAT_ENTERPRISE", CollaborationCapability.MESSAGE)).thenReturn(true);
        assertTrue(service.connections(11L).get(0).messageAvailable());
        when(socialApps.requireEnabledApp(7L, 22L, CollaborationCapability.MESSAGE))
                .thenThrow(new BusinessException("MESSAGE 应用已停用"));
        assertFalse(service.connections(11L).get(0).messageAvailable());
    }
    @Test void missingSecretIsNotReportedAsConfiguredOrAccepted() {
        loginConnection();
        when(socialApps.secretSummary(any())).thenReturn(SecretSummary.empty());
        assertFalse(service.connections(11L).get(0).loginAvailable());
        ApplicationIntegrationConfig dto = new ApplicationIntegrationConfig(); dto.setConnectionId(22L);
        assertThrows(BusinessException.class, () -> service.save(11L, dto));
        verifyNoInteractions(mapper, channels);
    }
    @Test void wecomMissingAgentAndDisabledLoginSwitchAreNotReady() {
        SysSocialConfig connection = loginConnection();
        SysSocialAppConfig app = configuredApp(); app.setAgentId(null);
        assertFalse(service.connections(11L).get(0).loginAvailable());
        app.setAgentId("100001"); connection.setSsoWorkbenchEnabled(0);
        assertFalse(service.connections(11L).get(0).loginAvailable());
    }
    @Test void sourceMustMatchPublishedApplicationSuiteAndObject() {
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO(); object.setSuiteCode("legal"); object.setObjectCode("contract");
        object.setObjectId(101L);
        AiBusinessObject source = new AiBusinessObject(); source.setId(101L);
        when(objects.selectByObjectCode(7L, "legal", "contract")).thenReturn(source);
        published.setObjects(List.of(object)); when(runtime.runtimeById(11L)).thenReturn(published);
        assertDoesNotThrow(() -> service.requireSource(11L, "legal", "contract"));
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "other", "contract"));
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "secret"));
    }
    @Test void unpublishedAppCannotRegister() {
        when(runtime.runtimeById(11L)).thenThrow(new BusinessException("尚未发布"));
        assertThrows(BusinessException.class, () -> service.requirePublish(11L, "new.code"));
        verifyNoInteractions(capabilities);
    }
    @Test void cannotUpgradeCapabilityOwnedElsewhere() {
        AiCapability capability = new AiCapability(); capability.setId(22L);
        when(capabilities.selectByCode(7L, "used.code")).thenReturn(capability);
        assertThrows(BusinessException.class, () -> service.requirePublish(11L, "used.code"));
        when(mapper.member(7L, 11L, 22L)).thenReturn(22L);
        assertDoesNotThrow(() -> service.requirePublish(11L, "used.code"));
    }
    @Test void syncsOnlyCapabilitiesOwnedByPublishedApplicationSources() {
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(101L); object.setSuiteCode("legal"); object.setObjectCode("contract");
        published.setObjects(List.of(object));
        when(runtime.runtimeById(11L)).thenReturn(published);
        when(mapper.unattachedPublishedCandidates(7L, 11L)).thenReturn(List.of(
                candidate(21L, "BUSINESS_ACTION", "legal/contract/confirm", "{}"),
                candidate(22L, "FLOW_ACTION", "legal/contract/APPROVE", "{}"),
                candidate(23L, "SYSTEM_SERVICE", "lowcode.form.create",
                        "{\"registrationParameters\":{\"suiteCode\":\"legal\",\"objectCode\":\"contract\"}}"),
                candidate(24L, "SYSTEM_SERVICE", "lowcode.business-process.start",
                        "{\"applicationId\":11,\"objectId\":101}"),
                candidate(25L, "SYSTEM_SERVICE", "system.rest.invoke", "{}"),
                candidate(26L, "BUSINESS_ACTION", "other/secret/run", "{}"),
                candidate(27L, "SYSTEM_SERVICE", "lowcode.form.create", "not-json")));
        when(mapper.attach(anyLong(), eq(7L), eq(11L), anyLong(), eq(8L), eq(9L))).thenReturn(1);

        assertEquals(4, service.syncCapabilities(11L));
        for (long id : List.of(21L, 22L, 23L, 24L)) {
            verify(mapper).attach(anyLong(), eq(7L), eq(11L), eq(id), eq(8L), eq(9L));
        }
        verify(mapper, never()).attach(anyLong(), eq(7L), eq(11L), eq(25L), anyLong(), anyLong());
        verify(mapper, never()).attach(anyLong(), eq(7L), eq(11L), eq(26L), anyLong(), anyLong());
        verify(mapper, never()).attach(anyLong(), eq(7L), eq(11L), eq(27L), anyLong(), anyLong());
    }
    @Test void catalogCapabilityCanBeClaimedOnlyWhenItsSourceBelongsToApplication() {
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(101L); object.setSuiteCode("legal"); object.setObjectCode("contract");
        published.setObjects(List.of(object));
        when(runtime.runtimeById(11L)).thenReturn(published);
        AiCapability capability = new AiCapability(); capability.setId(22L);
        when(capabilities.selectByCode(7L, "app_11.business.legal.contract.confirm")).thenReturn(capability);
        when(mapper.unattachedPublishedCandidates(7L, 11L)).thenReturn(List.of(
                candidate(22L, "BUSINESS_ACTION", "legal/contract/confirm", "{}")));
        when(mapper.attach(anyLong(), eq(7L), eq(11L), eq(22L), eq(8L), eq(9L))).thenReturn(1);
        when(mapper.member(7L, 11L, 22L)).thenReturn(22L);

        assertDoesNotThrow(() -> service.requirePublish(11L, "app_11.business.legal.contract.confirm"));
        verify(mapper).attach(anyLong(), eq(7L), eq(11L), eq(22L), eq(8L), eq(9L));
    }
    @Test void syncClaimsOnlyOneCapabilityWhenCatalogAlreadyContainsDuplicateSources() {
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setObjectId(101L); object.setSuiteCode("legal"); object.setObjectCode("contract");
        published.setObjects(List.of(object));
        when(runtime.runtimeById(11L)).thenReturn(published);
        when(mapper.unattachedPublishedCandidates(7L, 11L)).thenReturn(List.of(
                candidate(21L, "BUSINESS_ACTION", "legal/contract/confirm", "{}"),
                candidate(22L, "BUSINESS_ACTION", "legal/contract/confirm", "{}")));
        when(mapper.attach(anyLong(), eq(7L), eq(11L), eq(21L), eq(8L), eq(9L))).thenReturn(1);

        assertEquals(1, service.syncCapabilities(11L));
        verify(mapper).attach(anyLong(), eq(7L), eq(11L), eq(21L), eq(8L), eq(9L));
        verify(mapper, never()).attach(anyLong(), eq(7L), eq(11L), eq(22L), anyLong(), anyLong());
    }
    @Test void rejectsRegisteringTheSameApplicationSourceWithAnotherCode() throws Exception {
        ApplicationCapabilityCandidate existing = candidate(22L, "SYSTEM_SERVICE", "lowcode.form.create",
                "{\"registrationParameters\":{\"suiteCode\":\"legal\",\"objectCode\":\"contract\"}}");
        existing.setCapabilityCode("existing.form.create");
        when(mapper.applicationSourceCapabilities(7L, 11L, "SYSTEM_SERVICE", "lowcode.form.create"))
                .thenReturn(List.of(existing));
        ObjectMapper json = new ObjectMapper();

        assertThrows(BusinessException.class, () -> service.requireUniqueSource(
                11L, "new.form.create", "SYSTEM_SERVICE", "lowcode.form.create",
                json.readTree("{\"suiteCode\":\"legal\",\"objectCode\":\"contract\"}")));
        assertDoesNotThrow(() -> service.requireUniqueSource(
                11L, "existing.form.create", "SYSTEM_SERVICE", "lowcode.form.create",
                json.readTree("{\"suiteCode\":\"legal\",\"objectCode\":\"contract\"}")));
    }
    @Test void emptyOrForeignCapabilityNeverBecomesUnfilteredQuery() {
        assertThrows(BusinessException.class, () -> service.requireCapability(11L, null));
        assertThrows(BusinessException.class, () -> service.requireCapability(11L, 99L));
        verifyNoInteractions(capabilities);
    }

    private ApplicationCapabilityCandidate candidate(Long id, String sourceType, String sourceKey, String policy) {
        ApplicationCapabilityCandidate candidate = new ApplicationCapabilityCandidate();
        candidate.setCapabilityId(id);
        candidate.setSourceType(sourceType);
        candidate.setSourceKey(sourceKey);
        candidate.setPolicySnapshot(policy);
        return candidate;
    }
}
