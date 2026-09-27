package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessPublishService;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("BusinessApplication rollback boundary contract")
class BusinessApplicationRollbackContractTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("application rollback checks physical columns and never executes reverse DDL")
    void rollbackFailsClosedWithoutReverseDdl() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationRollbackService.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("历史版本依赖当前不存在的数据库字段"));
        assertTrue(source.contains("tableMappingService.getTableMapping"));
        assertTrue(source.contains("rollbackForApplication"));
        assertFalse(source.contains("syncDatabase("));
        assertFalse(source.contains("executeCreateTable("));
        assertFalse(source.contains("DROP COLUMN"));
    }

    @Test
    @DisplayName("application rollback rejects missing identity before run lookup")
    void rollbackRejectsMissingIdentityBeforeRunLookup() {
        BusinessApplicationPublishRunService runService = mock(BusinessApplicationPublishRunService.class);
        BusinessApplicationRollbackService service = new BusinessApplicationRollbackService(
                new ObjectMapper(),
                mock(BusinessApplicationVersionService.class),
                mock(BusinessApplicationService.class),
                mock(BusinessApplicationSnapshotService.class),
                runService,
                mock(BusinessObjectDesignVersionService.class),
                mock(BusinessObjectTableMappingService.class),
                mock(BusinessObjectPublishService.class),
                mock(BusinessProcessPublishService.class),
                mock(BusinessAppService.class),
                mock(BusinessApplicationPageMenuPublishService.class),
                mock(BusinessBindingMapper.class),
                mock(BusinessExtensionMapper.class),
                mock(BusinessExtensionVersionMapper.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.rollback(10L, 1, null, "rollback-identity-test-0001"));

        assertEquals("应用回滚缺少可信租户上下文", error.getMessage());
        verifyNoInteractions(runService);
    }
}
