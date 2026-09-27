package com.mdframe.forge.plugin.external.adapter.impl;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptAdapterTest {

    @Test
    void shouldRejectLegacyScriptWithoutExecutingIt() {
        ScriptAdapter adapter = new ScriptAdapter();

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.transform("data", "java.lang.Runtime.getRuntime().exec('id')"));

        assertEquals(ScriptAdapter.MIGRATION_MESSAGE, error.getMessage());
        assertFalse(adapter.validateConfig("result = response"));
    }
}
