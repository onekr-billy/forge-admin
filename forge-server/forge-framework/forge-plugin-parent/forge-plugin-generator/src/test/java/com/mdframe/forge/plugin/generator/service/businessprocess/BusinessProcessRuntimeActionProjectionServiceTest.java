package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessProcessRuntimeActionProjectionServiceTest {

    @Test
    void missingTenantContextFailsClosedBeforeReadingRuntimeMetadata() {
        Fixture fixture = fixture();
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> fixture.service.compileForObject("order", null));

            assertEquals("未获取到有效租户上下文", error.getMessage());
            verifyNoInteractions(fixture.objectMapper, fixture.versionMapper, fixture.processMapper,
                    fixture.applicationMapper);
        }
    }

    @Test
    void oneTrustedTenantIsUsedForTheWholeProjection() {
        Fixture fixture = fixture();
        when(fixture.objectMapper.selectFirstByObjectCode(9L, "order")).thenReturn(null);
        when(fixture.versionMapper.selectCurrentPublishedBySubjectObjectCode(9L, "order"))
                .thenReturn(List.of());
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(9L);

            assertEquals(List.of(), fixture.service.compileForObject("order", null));

            session.verify(SessionHelper::getTenantId);
            verify(fixture.objectMapper).selectFirstByObjectCode(9L, "order");
            verify(fixture.versionMapper).selectCurrentPublishedBySubjectObjectCode(9L, "order");
        }
    }

    private Fixture fixture() {
        BusinessProcessVersionMapper versionMapper = mock(BusinessProcessVersionMapper.class);
        BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
        BusinessApplicationMapper applicationMapper = mock(BusinessApplicationMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessProcessPublishService publishService = mock(BusinessProcessPublishService.class);
        BusinessProcessRuntimeActionCompiler compiler = mock(BusinessProcessRuntimeActionCompiler.class);
        BusinessProcessRuntimeActionProjectionService service =
                new BusinessProcessRuntimeActionProjectionService(
                        versionMapper, processMapper, applicationMapper, objectMapper,
                        publishService, compiler, new ObjectMapper());
        return new Fixture(service, versionMapper, processMapper, applicationMapper, objectMapper);
    }

    private record Fixture(
            BusinessProcessRuntimeActionProjectionService service,
            BusinessProcessVersionMapper versionMapper,
            BusinessProcessMapper processMapper,
            BusinessApplicationMapper applicationMapper,
            BusinessObjectMapper objectMapper) {
    }
}
