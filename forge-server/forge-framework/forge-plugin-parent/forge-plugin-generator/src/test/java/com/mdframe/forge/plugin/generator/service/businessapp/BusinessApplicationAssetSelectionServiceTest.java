package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.constant.BusinessExtensionStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessExtension;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPublishDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationAssetSelectionService")
class BusinessApplicationAssetSelectionServiceTest {

    @Test
    @DisplayName("default publish selection skips untested extension drafts")
    void defaultSelectionSkipsUntestedDrafts() {
        Set<Long> selected = BusinessApplicationAssetSelectionService.defaultPublishableExtensionIds(List.of(
                extension(1L, BusinessExtensionStatus.DRAFT.getCode()),
                extension(2L, BusinessExtensionStatus.TESTED.getCode()),
                extension(3L, BusinessExtensionStatus.ENABLED.getCode()),
                extension(4L, BusinessExtensionStatus.DISABLED.getCode())
        ));

        assertEquals(Set.of(2L, 3L, 4L), selected);
    }

    @Test
    @DisplayName("default entry selection skips disabled and incomplete runtime entries")
    void defaultEntrySelectionOnlyIncludesPublishableEntries() {
        Set<Long> selected = BusinessApplicationAssetSelectionService.defaultPublishableEntryIds(List.of(
                entry(1L, 0, "ROUTE", null),
                entry(2L, 1, "RUNTIME", null),
                entry(3L, 1, "RUNTIME", "crm_customer"),
                entry(4L, 1, "ROUTE", null)
        ));

        assertEquals(Set.of(3L, 4L), selected);
    }

    @Test
    @DisplayName("default process selection includes only enabled application processes")
    void defaultProcessSelectionOnlyIncludesEnabledProcesses() {
        AiBusinessProcess disabled = new AiBusinessProcess();
        disabled.setId(1L);
        disabled.setStatus(0);
        AiBusinessProcess enabled = new AiBusinessProcess();
        enabled.setId(2L);
        enabled.setStatus(1);

        Set<Long> selected = BusinessApplicationAssetSelectionService.defaultPublishableProcessIds(
                List.of(disabled, enabled));

        assertEquals(Set.of(2L), selected);
    }

    @Test
    @DisplayName("explicit entry selection skips disabled and incomplete entries")
    void explicitEntrySelectionSkipsUnpublishableEntries() {
        BusinessApplicationAssetSelectionService service = serviceWithEntries(List.of(
                entry(1L, 0, "ROUTE", null),
                entry(2L, 1, "RUNTIME", null),
                entry(3L, 1, "RUNTIME", "crm_customer")
        ));
        BusinessApplicationPublishDTO dto = new BusinessApplicationPublishDTO();
        dto.setSelectedEntryIds(List.of(1L, 2L, 3L));
        dto.setIncludeAutomation(false);

        BusinessApplicationAssetSelectionService.ResolvedSelection resolved = service.resolveContext(99L, dto);

        assertEquals(List.of(3L), resolved.selection().getEntryIds());
        assertTrue(resolved.selection().getDependencyMessages().stream()
                .anyMatch(message -> message.contains("2 个未启用或未完成配置的访问入口")));
    }

    @Test
    @DisplayName("an explicit empty entry selection stays empty")
    void explicitEmptyEntrySelectionStaysEmpty() {
        BusinessApplicationAssetSelectionService service = serviceWithEntries(List.of(
                entry(1L, 1, "ROUTE", null)
        ));
        BusinessApplicationPublishDTO dto = new BusinessApplicationPublishDTO();
        dto.setSelectedEntryIds(List.of());
        dto.setIncludeAutomation(false);

        assertEquals(List.of(), service.resolveContext(99L, dto).selection().getEntryIds());
    }

    @Test
    @DisplayName("explicit foreign entry id still fails closed")
    void explicitForeignEntryIdStillFailsClosed() {
        BusinessApplicationAssetSelectionService service = serviceWithEntries(List.of(
                entry(1L, 1, "ROUTE", null)
        ));
        BusinessApplicationPublishDTO dto = new BusinessApplicationPublishDTO();
        dto.setSelectedEntryIds(List.of(1L, 999L));
        dto.setIncludeAutomation(false);

        assertThrows(BusinessException.class, () -> service.resolveContext(99L, dto));
    }

    private BusinessApplicationAssetSelectionService serviceWithEntries(List<AiBusinessApp> entries) {
        BusinessApplicationObjectService objectService = mock(BusinessApplicationObjectService.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        BusinessExtensionMapper extensionMapper = mock(BusinessExtensionMapper.class);
        BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
        when(objectService.list(99L)).thenReturn(List.of());
        when(appMapper.selectByApplicationId(1L, 99L)).thenReturn(entries);
        when(extensionMapper.selectByApplicationId(1L, 99L)).thenReturn(List.of());
        when(processMapper.selectByApplicationId(1L, 99L)).thenReturn(List.of());
        return new BusinessApplicationAssetSelectionService(
                objectService, appMapper, extensionMapper, processMapper);
    }

    private AiBusinessExtension extension(Long id, String status) {
        AiBusinessExtension extension = new AiBusinessExtension();
        extension.setId(id);
        extension.setStatus(status);
        return extension;
    }

    private AiBusinessApp entry(Long id, Integer status, String entryMode, String configKey) {
        AiBusinessApp entry = new AiBusinessApp();
        entry.setId(id);
        entry.setStatus(status);
        entry.setEntryMode(entryMode);
        entry.setConfigKey(configKey);
        entry.setAppName("页面 " + id);
        return entry;
    }
}
