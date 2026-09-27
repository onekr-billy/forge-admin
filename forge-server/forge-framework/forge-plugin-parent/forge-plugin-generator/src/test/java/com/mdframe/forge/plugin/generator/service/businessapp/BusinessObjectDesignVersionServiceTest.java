package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignVersionDTO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Business object design version allocation")
class BusinessObjectDesignVersionServiceTest {

    private ExecutionIdentityContextHolder.Scope identityScope;

    @BeforeEach
    void setUpIdentity() {
        LoginUser user = new LoginUser();
        user.setTenantId(1L);
        user.setUserId(101L);
        user.setUsername("designer");
        identityScope = ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                user, "USER", 101L, null, 301L,
                "object_version_test", "token-object-version", Set.of()));
    }

    @AfterEach
    void clearIdentity() {
        if (identityScope != null) {
            identityScope.close();
            identityScope = null;
        }
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("allocates the object history sequence independently from the linked CRUD publish version")
    void allocatesDesignSequenceIndependentlyFromCrudPublishVersion() {
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        AiBusinessObject object = new AiBusinessObject();
        object.setId(1910000000000001111L);
        object.setTenantId(1L);
        object.setSuiteCode("PRESALE_REGISTRATION");
        object.setObjectCode("PS_PRESALE_ORDER");
        when(objectService.requireEntity(1L, object.getId())).thenReturn(object);

        CapturingDesignVersionService service = new CapturingDesignVersionService(
                new ObjectMapper(), objectService, 2);
        BusinessObjectDesignVersionDTO dto = new BusinessObjectDesignVersionDTO();
        dto.setObjectId(object.getId());
        dto.setPublishVersion(1);
        dto.setPublishStatus("PUBLISHED");

        service.createVersion(dto);

        assertEquals(2, service.savedVersion.getVersionNo());
        assertEquals(1, service.savedVersion.getPublishVersion());
        assertEquals(1L, service.savedVersion.getTenantId());
    }

    @Test
    @DisplayName("rejects missing identity before reading or persisting an object version")
    void rejectsMissingIdentityBeforePersistence() {
        identityScope.close();
        identityScope = null;
        ExecutionIdentityContextHolder.clear();
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        BusinessObjectDesignVersionService service = new BusinessObjectDesignVersionService(
                new ObjectMapper(), objectService);
        BusinessObjectDesignVersionDTO dto = new BusinessObjectDesignVersionDTO();
        dto.setObjectId(99L);

        assertThrows(BusinessException.class, () -> service.createVersion(dto));

        verifyNoInteractions(objectService);
    }

    private static final class CapturingDesignVersionService extends BusinessObjectDesignVersionService {

        private final int nextVersion;
        private AiBusinessObjectDesignVersion savedVersion;

        private CapturingDesignVersionService(ObjectMapper objectMapper,
                                              BusinessObjectService objectService,
                                              int nextVersion) {
            super(objectMapper, objectService);
            this.nextVersion = nextVersion;
        }

        @Override
        protected Integer nextVersionNo(Long tenantId, Long objectId) {
            return nextVersion;
        }

        @Override
        public boolean save(AiBusinessObjectDesignVersion entity) {
            savedVersion = entity;
            entity.setId(1L);
            return true;
        }
    }
}
