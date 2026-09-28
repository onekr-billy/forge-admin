package com.mdframe.forge.plugin.external.adapter.impl;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngineManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScriptAdapterTest {

    @Test
    void shouldReturnBusinessErrorWhenEngineIsUnavailable() {
        ScriptEngineManager manager = mock(ScriptEngineManager.class);
        when(manager.getEngineByName("javascript")).thenReturn(null);
        ScriptAdapter adapter = new ScriptAdapter(manager);

        assertThrows(BusinessException.class, () -> adapter.transform("data", "result = response"));
    }
}
