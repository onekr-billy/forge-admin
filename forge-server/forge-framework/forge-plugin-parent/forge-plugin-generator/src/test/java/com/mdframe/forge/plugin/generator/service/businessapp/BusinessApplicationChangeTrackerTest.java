package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("BusinessApplicationChangeTracker")
class BusinessApplicationChangeTrackerTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("missing tenant is rejected before application state mutation")
    void missingTenantIsRejectedBeforeMutation() {
        BusinessApplicationMapper mapper = mock(BusinessApplicationMapper.class);
        BusinessApplicationChangeTracker tracker = new BusinessApplicationChangeTracker(mapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> tracker.markApplicationChanged(10L));

        assertEquals("应用变更标记缺少可信租户上下文", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("captured tenant is reused by application change mutation")
    void explicitTenantIsUsedForMutation() {
        BusinessApplicationMapper mapper = mock(BusinessApplicationMapper.class);
        BusinessApplicationChangeTracker tracker = new BusinessApplicationChangeTracker(mapper);

        tracker.markApplicationChanged(9L, 10L);

        verify(mapper).markChanged(9L, 10L);
    }
}
