package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Business object design trusted identity")
class BusinessObjectDesignIdentitySecurityTest {

    private ExecutionIdentityContextHolder.Scope identityScope;

    @AfterEach
    void clearIdentity() {
        if (identityScope != null) {
            identityScope.close();
            identityScope = null;
        }
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("business object reads reject a missing tenant before mapper access")
    void objectReadRejectsMissingTenant() {
        BusinessObjectService service = new BusinessObjectService(null, null, null, null, null);

        assertThrows(BusinessException.class, () -> service.detail(10L));
    }

    @Test
    @DisplayName("designer context rejects a missing tenant before loading an object")
    void designerRejectsMissingTenant() {
        BusinessObjectDesignerService service = designerService();

        assertThrows(BusinessException.class, () -> service.loadContext(10L));
    }

    @Test
    @DisplayName("designer writes reject a context owned by another tenant")
    void designerRejectsCrossTenantContext() {
        openIdentity(1L, 101L);
        BusinessObjectDesignerService service = designerService();
        BusinessObjectDesignerService.DesignerContext context = context(2L);

        assertThrows(BusinessException.class, () -> service.saveDraft(context, "CHANGED"));
    }

    @Test
    @DisplayName("preview cache is isolated by tenant and object")
    void previewCacheIsTenantScoped() {
        BusinessObjectDesignerService service = designerService();
        AiCrudConfig tenantOneConfig = new AiCrudConfig();
        tenantOneConfig.setId(11L);
        AiCrudConfig tenantTwoConfig = new AiCrudConfig();
        tenantTwoConfig.setId(22L);
        doReturn(tenantOneConfig, tenantTwoConfig).when(service).prepareRuntimeDraft(10L, false);

        openIdentity(1L, 101L);
        assertSame(tenantOneConfig, service.prepareRuntimeDraftForPreview(10L));
        closeIdentity();
        openIdentity(2L, 202L);
        assertSame(tenantTwoConfig, service.prepareRuntimeDraftForPreview(10L));

        verify(service, times(2)).prepareRuntimeDraft(10L, false);
    }

    @Test
    @DisplayName("publishing rejects a missing actor before updating object state")
    void publishRejectsMissingActor() {
        BusinessObjectPublishService service = mock(BusinessObjectPublishService.class, CALLS_REAL_METHODS);

        assertThrows(BusinessException.class, () -> service.markDesignPublished(List.of(10L)));
    }

    @Test
    @DisplayName("publishing rejects a preloaded context owned by another tenant")
    void publishRejectsCrossTenantContext() {
        openIdentity(1L, 101L);
        BusinessObjectPublishService service = mock(BusinessObjectPublishService.class, CALLS_REAL_METHODS);

        assertThrows(BusinessException.class, () -> service.publish(10L, null, null, context(2L)));
    }

    private BusinessObjectDesignerService designerService() {
        return spy(new BusinessObjectDesignerService(
                new ObjectMapper(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));
    }

    private BusinessObjectDesignerService.DesignerContext context(Long tenantId) {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(10L);
        object.setTenantId(tenantId);
        BusinessObjectDesignerService.DesignerContext context = new BusinessObjectDesignerService.DesignerContext();
        context.setObject(object);
        return context;
    }

    private void openIdentity(Long tenantId, Long userId) {
        LoginUser user = new LoginUser();
        user.setTenantId(tenantId);
        user.setUserId(userId);
        user.setUsername("designer-" + userId);
        identityScope = ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                user, "USER", userId, null, 301L,
                "object_design_security_test", "token-" + userId, Set.of()));
    }

    private void closeIdentity() {
        if (identityScope != null) {
            identityScope.close();
            identityScope = null;
        }
        ExecutionIdentityContextHolder.clear();
    }
}
