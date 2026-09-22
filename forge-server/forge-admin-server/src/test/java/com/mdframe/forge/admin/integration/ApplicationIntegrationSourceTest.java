package com.mdframe.forge.admin.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.admin.integration.mapper.ApplicationIntegrationMapper;
import com.mdframe.forge.admin.integration.service.ApplicationIntegrationService;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.*;
import com.mdframe.forge.plugin.generator.service.businessapp.*;
import com.mdframe.forge.plugin.generator.service.printing.PrintApplicationSnapshotContributor;
import com.mdframe.forge.plugin.generator.vo.businessapp.*;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise the real snapshot writer/reader, not a hand-populated runtime VO. */
class ApplicationIntegrationSourceTest {
    private static final Long OBJECT_ID = 2100942720360046594L;
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final BusinessApplicationService applications = mock(BusinessApplicationService.class);
    private final BusinessApplicationObjectService applicationObjects = mock(BusinessApplicationObjectService.class);
    private final BusinessApplicationVersionService versions = mock(BusinessApplicationVersionService.class);
    private final BusinessObjectMapper objects = mock(BusinessObjectMapper.class);
    private final BusinessApplicationVO application = new BusinessApplicationVO();
    private final AiBusinessApplicationVersion version = new AiBusinessApplicationVersion();
    private org.mockito.MockedStatic<SessionHelper> session;
    private ApplicationIntegrationService service;
    private BusinessApplicationRuntimeService runtime;

    @BeforeEach
    void setup() {
        session = mockStatic(SessionHelper.class);
        session.when(SessionHelper::getTenantId).thenReturn(7L);
        application.setId(11L);
        application.setApplicationCode("workspace");
        application.setSuiteCode("workspace");
        application.setStatus(1);
        application.setLastPublishVersion(3);
        when(applications.detail(11L)).thenReturn(application);
        when(applications.canCurrentUserAccessPortal(anyString())).thenReturn(true);
        AiBusinessApplication draft = new AiBusinessApplication();
        draft.setId(11L);
        draft.setApplicationCode("workspace");
        draft.setSuiteCode("workspace");
        draft.setStatus(1);
        when(applications.requireEntity(11L)).thenReturn(draft);

        BusinessApplicationObjectVO bound = new BusinessApplicationObjectVO();
        bound.setObjectId(OBJECT_ID);
        bound.setObjectCode("contract");
        bound.setSuiteCode("legal"); // A reused object's suite need not equal the application's suite.
        when(applicationObjects.list(11L)).thenReturn(List.of(bound));
        BusinessApplicationSnapshotService snapshots = new BusinessApplicationSnapshotService(json,
                applications, applicationObjects, mock(BusinessAppService.class),
                mock(BusinessBindingMapper.class), mock(BusinessExtensionMapper.class),
                mock(BusinessExtensionVersionMapper.class), mock(BusinessPermissionService.class),
                mock(BusinessProcessMapper.class), mock(PrintApplicationSnapshotContributor.class));
        BusinessApplicationAssetSelectionVO selection = new BusinessApplicationAssetSelectionVO();
        selection.setObjectIds(List.of(OBJECT_ID));
        version.setVersionNo(3);
        version.setSnapshotJson(snapshots.prepare(11L, selection).json());
        when(versions.requireVersion(11L, 3)).thenReturn(version);
        runtime = new BusinessApplicationRuntimeService(applications, versions, snapshots, json);
        service = new ApplicationIntegrationService(mock(ApplicationIntegrationMapper.class), applications,
                runtime, objects, mock(AiCapabilityMapper.class), mock(BusinessMessageChannelMapper.class),
                mock(ISocialConfigService.class), mock(ISocialAppConfigService.class),
                mock(CollaborationProviderRegistry.class));
        source("legal", "contract", OBJECT_ID);
    }

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void generatedSnapshotKeepsObjectSuiteAndCanRegister() {
        assertEquals("legal", runtime.runtimeById(11L).getObjects().get(0).getSuiteCode());
        assertDoesNotThrow(() -> service.requireSource(11L, "legal", "contract"));
    }

    @Test
    void legacySnapshotCanRegisterWithoutRepublishingOrUsingApplicationSuite() throws Exception {
        legacySnapshot();
        String immutableSnapshot = version.getSnapshotJson();
        assertNull(runtime.runtimeById(11L).getObjects().get(0).getSuiteCode());
        assertDoesNotThrow(() -> service.requireSource(11L, "legal", "contract"));
        assertEquals(immutableSnapshot, version.getSnapshotJson());
        verify(objects).selectByObjectCode(7L, "legal", "contract");
    }

    @Test
    void legacySnapshotRejectsSameCodeInAnotherSuite() throws Exception {
        legacySnapshot();
        source("other", "contract", OBJECT_ID + 1);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "other", "contract"));
    }

    @Test
    void draftOnlyObjectIsNotPartOfPublishedApplication() throws Exception {
        legacySnapshot();
        source("legal", "draft_only", OBJECT_ID + 1);
        clearInvocations(applicationObjects);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "draft_only"));
        verifyNoInteractions(applicationObjects);
    }

    @Test
    void deletedObjectCannotRegister() throws Exception {
        legacySnapshot();
        when(objects.selectByObjectCode(7L, "legal", "contract")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
    }

    @Test
    void recreatedObjectWithSameCodeHasDifferentIdentity() throws Exception {
        legacySnapshot();
        source("legal", "contract", OBJECT_ID + 1);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
    }

    @Test
    void explicitSnapshotSuiteMismatchDoesNotUseLegacyFallback() throws Exception {
        ObjectNode snapshot = (ObjectNode) json.readTree(version.getSnapshotJson());
        ((ObjectNode) snapshot.path("objects").get(0)).put("suiteCode", "other");
        version.setSnapshotJson(snapshot.toString());
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
    }

    @Test
    void legacySnapshotWithoutObjectIdFailsClosed() throws Exception {
        legacySnapshot();
        ObjectNode snapshot = (ObjectNode) json.readTree(version.getSnapshotJson());
        ((ObjectNode) snapshot.path("objects").get(0)).remove("objectId");
        version.setSnapshotJson(snapshot.toString());
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingSourceCodesNeverStartAnObjectLookup(String missing) {
        assertThrows(BusinessException.class, () -> service.requireSource(11L, missing, "contract"));
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", missing));
        verifyNoInteractions(objects);
    }

    @Test
    void missingTenantFailsBeforeLookup() {
        session.when(SessionHelper::getTenantId).thenReturn(null);
        clearInvocations(applications);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
        verifyNoInteractions(objects, applications);
    }

    @Test
    void currentTenantCannotResolveObjectFromAnotherTenant() {
        session.when(SessionHelper::getTenantId).thenReturn(8L);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
        verify(objects).selectByObjectCode(8L, "legal", "contract");
    }

    @Test
    void unpublishedApplicationStillFails() {
        application.setLastPublishVersion(null);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
        verifyNoInteractions(objects);
    }

    @Test
    void portalAccessDenialStillFails() {
        when(applications.canCurrentUserAccessPortal(anyString())).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.requireSource(11L, "legal", "contract"));
        verifyNoInteractions(objects);
    }

    private void legacySnapshot() throws Exception {
        ObjectNode snapshot = (ObjectNode) json.readTree(version.getSnapshotJson());
        ((ObjectNode) snapshot.path("objects").get(0)).remove("suiteCode");
        version.setSnapshotJson(snapshot.toString());
    }

    private void source(String suite, String code, Long id) {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(id);
        object.setTenantId(7L);
        object.setSuiteCode(suite);
        object.setObjectCode(code);
        when(objects.selectByObjectCode(7L, suite, code)).thenReturn(object);
    }
}
