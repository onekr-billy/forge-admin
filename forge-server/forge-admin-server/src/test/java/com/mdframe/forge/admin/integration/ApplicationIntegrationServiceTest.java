package com.mdframe.forge.admin.integration;

import com.mdframe.forge.admin.integration.dto.ApplicationIntegrationConfig;
import com.mdframe.forge.admin.integration.mapper.ApplicationIntegrationMapper;
import com.mdframe.forge.admin.integration.service.ApplicationIntegrationService;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessMessageChannel;
import com.mdframe.forge.plugin.generator.mapper.BusinessMessageChannelMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationRuntimeService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationService;
import com.mdframe.forge.plugin.generator.vo.businessapp.*;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
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
    AiCapabilityMapper capabilities = mock(AiCapabilityMapper.class);
    BusinessMessageChannelMapper channels = mock(BusinessMessageChannelMapper.class);
    ISocialConfigService connections = mock(ISocialConfigService.class);
    ISocialAppConfigService socialApps = mock(ISocialAppConfigService.class);
    CollaborationProviderRegistry providers = mock(CollaborationProviderRegistry.class);
    ApplicationIntegrationService service = new ApplicationIntegrationService(mapper, applications, runtime,
            capabilities, channels, connections, socialApps, providers);
    MockedStatic<SessionHelper> session;

    @BeforeEach void setup() {
        session = mockStatic(SessionHelper.class);
        session.when(SessionHelper::getTenantId).thenReturn(7L);
        session.when(SessionHelper::getUserId).thenReturn(8L);
        session.when(SessionHelper::getActiveOrgId).thenReturn(9L);
        BusinessApplicationVO app = new BusinessApplicationVO();
        app.setId(11L); app.setApplicationCode("legal"); app.setApplicationName("法律协同");
        when(applications.detail(11L)).thenReturn(app);
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
        connection.setPlatform("WECHAT_ENTERPRISE"); connection.setStatus(1);
        when(connections.selectConfigById(22L)).thenReturn(connection);
        when(providers.supports("WECHAT_ENTERPRISE", CollaborationCapability.MESSAGE)).thenReturn(true);
        when(mapper.save(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
        ApplicationIntegrationConfig dto = new ApplicationIntegrationConfig(); dto.setConnectionId(22L);
        service.save(11L, dto);
        verify(channels).insert(argThat((AiBusinessMessageChannel c) -> c.getTenantId().equals(7L)
                && c.getChannelType().equals("COLLABORATION") && c.getChannelConfigRef().equals("22") && c.getStatus() == 1));
    }
    @Test void sourceMustMatchPublishedApplicationSuiteAndObject() {
        BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO(); object.setSuiteCode("legal"); object.setObjectCode("contract");
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
    @Test void emptyOrForeignCapabilityNeverBecomesUnfilteredQuery() {
        assertThrows(BusinessException.class, () -> service.requireCapability(11L, null));
        assertThrows(BusinessException.class, () -> service.requireCapability(11L, 99L));
        verifyNoInteractions(capabilities);
    }
}
