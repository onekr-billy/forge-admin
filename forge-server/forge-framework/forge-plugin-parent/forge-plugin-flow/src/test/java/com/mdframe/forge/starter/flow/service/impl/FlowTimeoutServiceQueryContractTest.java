package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowTimeoutServiceQueryContractTest {

    @Test
    void timeoutScanMustPageActiveTasks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowTimeoutServiceImpl.java"));
        assertTrue(source.contains(".listPage("));
        assertFalse(source.contains(".list();"));
    }

    @Test
    void candidateTasksMustUseMapperPaging() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowTaskServiceImpl.java"));
        int start = source.indexOf("public IPage<FlowTask> candidateTasks");
        int end = source.indexOf("private IPage<FlowTask> enrichTaskPage", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("selectCandidateTasks"));
        assertFalse(method.contains(".list()"));
        assertFalse(method.contains("createTaskQuery"));
    }
}
