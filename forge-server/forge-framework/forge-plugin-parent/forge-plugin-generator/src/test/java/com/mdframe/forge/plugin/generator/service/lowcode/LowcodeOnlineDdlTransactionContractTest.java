package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowcodeOnlineDdlTransactionContractTest {

    @Test
    void publishWorkflowDdlExplicitlySuspendsLocalTransaction() throws NoSuchMethodException {
        Transactional transactional = LowcodeDdlService.class
                .getMethod("executeCreateTableWithoutTransaction", LowcodeModelSchema.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
    }
}
