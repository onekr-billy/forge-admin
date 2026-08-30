package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.mapper.FlowModelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("flow model catalog tenant boundary")
class FlowModelServiceImplTest {

    @Test
    @DisplayName("enabled model catalog is queried with the trusted current tenant")
    void enabledModelsUseTrustedTenant() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);
        FlowModel model = new FlowModel();
        model.setModelKey("leave_approval");
        when(mapper.selectEnabledModels(7L, "approval")).thenReturn(List.of(model));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            List<FlowModel> result = service.getEnabledModels("approval");

            assertEquals(List.of(model), result);
            verify(mapper).selectEnabledModels(7L, "approval");
        }
    }

    @Test
    @DisplayName("missing tenant context fails closed without querying the catalog")
    void missingTenantContextFailsClosed() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertEquals(List.of(), service.getEnabledModels(null));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    @DisplayName("model key lookup is scoped to the trusted current tenant")
    void modelKeyLookupUsesTrustedTenant() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);
        FlowModel model = new FlowModel();
        model.setModelKey("leave_approval");
        when(mapper.selectByModelKeyAndTenantId("leave_approval", 7L)).thenReturn(model);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            assertEquals(model, service.getModelByKey(" leave_approval "));
            verify(mapper).selectByModelKeyAndTenantId("leave_approval", 7L);
        }
    }

    @Test
    @DisplayName("model key lookup fails closed without a trusted tenant")
    void modelKeyLookupWithoutTenantFailsClosed() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertNull(service.getModelByKey("leave_approval"));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    @DisplayName("duplicate model key check uses tenant and excluded model id")
    void duplicateModelKeyCheckUsesTenantAndExcludedId() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);
        when(mapper.countByModelKeyAndTenantId("leave_approval", 7L, "model-1"))
                .thenReturn(1L);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(7L);

            assertTrue(service.checkModelKeyExists(" leave_approval ", "model-1"));
            verify(mapper).countByModelKeyAndTenantId("leave_approval", 7L, "model-1");
        }
    }

    @Test
    @DisplayName("duplicate model key check fails closed without a trusted tenant")
    void duplicateModelKeyCheckWithoutTenantFailsClosed() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(null);

            assertFalse(service.checkModelKeyExists("leave_approval", null));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    @DisplayName("copying a model preserves the multi-level return switch")
    void copyModelPreservesMultiLevelReturnSwitch() {
        FlowModelMapper mapper = mock(FlowModelMapper.class);
        TestFlowModelService service = new TestFlowModelService(mapper);
        FlowModel source = new FlowModel();
        source.setModelKey("leave_approval");
        source.setDesignerType("approval");
        source.setAllowMultiReturn(true);
        source.setBpmnXml("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="leave_approval"/>
                </definitions>
                """);
        service.sourceModel = source;
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername("reviewer");

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getLoginUser).thenReturn(loginUser);

            FlowModel copy = service.copyModel("model-1", "请假审批副本");

            assertTrue(Boolean.TRUE.equals(copy.getAllowMultiReturn()));
            assertEquals(copy, service.savedModel);
        }
    }

    private static final class TestFlowModelService extends FlowModelServiceImpl {

        private FlowModel sourceModel;
        private FlowModel savedModel;

        private TestFlowModelService(FlowModelMapper mapper) {
            this.baseMapper = mapper;
        }

        @Override
        public FlowModel getById(Serializable id) {
            return sourceModel;
        }

        @Override
        public boolean save(FlowModel entity) {
            savedModel = entity;
            return true;
        }
    }
}
