package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignVersionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Business object design version allocation")
class BusinessObjectDesignVersionServiceTest {

    @Test
    @DisplayName("allocates the object history sequence independently from the linked CRUD publish version")
    void allocatesDesignSequenceIndependentlyFromCrudPublishVersion() {
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        AiBusinessObject object = new AiBusinessObject();
        object.setId(1910000000000001111L);
        object.setSuiteCode("PRESALE_REGISTRATION");
        object.setObjectCode("PS_PRESALE_ORDER");
        when(objectService.requireEntity(object.getId())).thenReturn(object);

        CapturingDesignVersionService service = new CapturingDesignVersionService(
                new ObjectMapper(), objectService, 2);
        BusinessObjectDesignVersionDTO dto = new BusinessObjectDesignVersionDTO();
        dto.setObjectId(object.getId());
        dto.setPublishVersion(1);
        dto.setPublishStatus("PUBLISHED");

        service.createVersion(dto);

        assertEquals(2, service.savedVersion.getVersionNo());
        assertEquals(1, service.savedVersion.getPublishVersion());
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
        public Integer nextVersionNo(Long objectId) {
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
