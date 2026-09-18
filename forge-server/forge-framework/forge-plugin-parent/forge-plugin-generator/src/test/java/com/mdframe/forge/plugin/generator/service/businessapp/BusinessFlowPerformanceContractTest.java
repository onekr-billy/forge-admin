package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowPerformanceContractTest {

    @Test
    void businessTaskContextMustReuseOneFlowFormSnapshot() throws IOException {
        String source = serviceSource();
        String method = method(source, "public BusinessTaskFormContextVO getTaskFormContext", "    /**", 1);

        assertTrue(method.contains("Map<String, Object> taskFormInfo = loadTaskFormInfo"));
        assertTrue(method.contains("validateTaskAccess(effectiveQuery, false, taskFormInfo)"));
        assertTrue(method.contains("resolveTaskFormRuntimeContext(effectiveQuery, false, taskFormInfo)"));
        assertTrue(method.contains("buildTaskFormContext(effectiveQuery, runtime, taskFormInfo)"));
        assertFalse(source.contains("flowClient.getTaskDetail("));
    }

    @Test
    void businessTaskActionMustPersistSubmittedFormDataBeforeCallingFlow() throws IOException {
        String source = serviceSource();
        String method = method(source, "public BusinessFlowRuntimeVO completeBusinessTask", "    /**", 1);

        assertTrue(method.contains("dto.getData() != null && !dto.getData().isEmpty()"));
        assertTrue(method.contains("persistTaskFormData("));
        assertTrue(method.indexOf("persistTaskFormData(") < method.indexOf("flowClient.approve("));
        assertTrue(source.contains("dynamicCrudService.updateTaskEditableData"));
    }

    @Test
    void listDisplayMustReuseRuntimeObjectMetadata() throws IOException {
        String source = serviceSource();

        assertTrue(source.contains("Map<String, AiBusinessObject> objectLookupCache"));
        assertTrue(source.contains("context.businessObject() == null"));
        assertTrue(source.contains("toBusinessObjectVO(context.businessObject())"));
    }

    private String serviceSource() throws IOException {
        return Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowService.java"));
    }

    private String method(String source, String signature, String nextToken, int tokenOffset) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing method: " + signature);
        int end = source.indexOf(nextToken, start + signature.length());
        assertTrue(end > start, "missing method boundary: " + signature);
        return source.substring(start, end + tokenOffset);
    }

    private Path resolveSource(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of(relativePath);
    }
}
