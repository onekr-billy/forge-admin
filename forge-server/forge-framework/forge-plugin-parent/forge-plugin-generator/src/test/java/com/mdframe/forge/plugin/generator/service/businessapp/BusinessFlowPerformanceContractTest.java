package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowPerformanceContractTest {

    @Test
    void businessTaskContextMustReuseOneFlowFormSnapshot() throws IOException {
        String source = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskFormContextCoordinator.java"));
        String method = method(source, "private BusinessTaskFormContextVO getActiveTaskFormContext", "    private BusinessTaskFormContextQueryDTO effectiveQuery", 1);

        assertTrue(method.contains("Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo"));
        assertTrue(method.contains("taskAccessPolicy.validate("));
        assertTrue(method.contains("runtimeContextResolver.resolveTask"));
        assertTrue(method.contains("effectiveQuery, writeRequired, taskFormInfo"));
        assertTrue(method.contains("buildTaskFormContext(effectiveQuery, runtime, taskFormInfo"));
        assertTrue(method.contains("runtimeContextMs"));
        assertFalse(source.contains("flowClient.getTaskDetail("));
    }

    @Test
    void businessTaskContextMustReuseRuntimeConfigAndSlimFormAssets() throws IOException {
        String source = serviceSource();
        String contextCoordinator = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskFormContextCoordinator.java"));
        String applicationPageResolver = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowApplicationPageFormResolver.java"));
        String nodeFormResolver = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskNodeFormResolver.java"));
        String runtimeContextResolver = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowRuntimeContextResolver.java"));
        String taskFormSchemaAssembler = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskFormSchemaAssembler.java"));

        assertTrue(contextCoordinator.contains("taskFormSchemaAssembler.safeGetRuntimeConfig(runtime.configKey())"));
        assertTrue(taskFormSchemaAssembler.contains("slimTaskFormAssets("));
        assertTrue(source.contains("taskFormSchemaAssembler::applyRuntimeCrudFormLayout"));
        assertTrue(source.contains("applicationPageFormResolver"));
        assertTrue(applicationPageResolver.contains("pageAssetCache"));
        assertTrue(contextCoordinator.contains("[task-form-context]"));
        assertTrue(applicationPageResolver.contains("loadInAppBuilder("));
        assertTrue(contextCoordinator.contains("selectById(runtimeConfig, runtime.recordId())"));
        assertTrue(contextCoordinator.contains("runtime.publishedConfig()"));
        assertTrue(contextCoordinator.contains("runtime.businessObject()"));
        assertTrue(contextCoordinator.contains(
                "formSchemaResolver.resolve(\n                object, formKey, runtime.configKey(), runtimeConfig)"));
        assertTrue(runtimeContextResolver.contains("BusinessFlowBindingCodec.ensureBusinessBinding("));
        assertTrue(runtimeContextResolver.contains(
                "bindingConfig, businessContext.runtimeConfig(), businessContext.documentConfig())"));
        assertTrue(nodeFormResolver.contains("processFormRpc=skip(queryOrVarFormKey)"));
        assertTrue(applicationPageResolver.contains("loadCachedInAppBuilder("));
        assertTrue(runtimeContextResolver.contains("pageAssetMs"));
        assertTrue(runtimeContextResolver.contains("businessContextMs"));
        assertTrue(nodeFormResolver.contains("collectTaskFormAssets=skip(appFormKey)"));
        assertTrue(applicationPageResolver.contains("parseApplicationPageFormKey("));
        assertTrue(applicationPageResolver.contains("findApplicationFormAsset("));
        assertFalse(applicationPageResolver.contains(
                "appendRuntimeChildFieldCatalog(StringUtils.trimToNull(objectRef.getString(\"configKey\")), fields)"));
    }

    @Test
    void flowNodeFormInfoShouldSkipSecondRpcWhenFormKeyPresent() throws IOException {
        String source = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskNodeFormResolver.java"));
        String method = method(source, "private boolean isCompleteFlowNodeFormInfo", "    Map<String, Object> loadTaskFormInfo", 0);
        assertTrue(method.contains("formInfo.get(\"formKey\")"));
        assertTrue(method.contains("formInfo.get(\"formRef\") instanceof Map"));
        assertTrue(method.contains("resolveRuntimeBusinessFormRef(formInfo)"));
        assertTrue(source.contains("slimPageFormAssetMeta("));
        assertTrue(source.contains("StringUtils.startsWith(StringUtils.trimToEmpty(formKey), \"app_\")"));
    }
    @Test
    void businessTaskActionMustPersistSubmittedFormDataBeforeCallingFlow() throws IOException {
        String source = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskCommandCoordinator.java"));
        String service = serviceSource();
        String method = method(source, "BusinessFlowRuntimeVO completeBusinessTask",
                "    private BusinessFlowRemoteTaskRequest prepareTaskAction", 0);
        String preparation = method(source, "private BusinessFlowRemoteTaskRequest prepareTaskAction",
                "    BusinessFlowRuntimeVO recoverCapabilityTaskAction", 0);
        int serviceMethod = service.indexOf("public BusinessFlowRuntimeVO completeBusinessTask");

        assertTrue(serviceMethod > 80);
        assertFalse(service.substring(serviceMethod - 80, serviceMethod).contains("@Transactional"));
        assertTrue(method.contains("requiresNew(() -> prepareTaskAction("));
        assertTrue(method.indexOf("prepareTaskAction(") < method.indexOf("remoteCommandService.prepareTask("));
        assertTrue(method.indexOf("remoteCommandService.prepareTask(")
                < method.indexOf("remoteCommandService.executeTask("));
        assertTrue(method.contains("requiresNew(() -> persistRecoveredTaskCommand("));
        assertTrue(preparation.contains("dto.getData() != null && !dto.getData().isEmpty()"));
        assertTrue(preparation.contains("persistTaskFormData("));
        assertTrue(source.contains("dynamicCrudService.updateTaskEditableData"));
    }

    @Test
    void listDisplayMustReuseRuntimeObjectMetadata() throws IOException {
        String source = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowListDisplayEnricher.java"));

        assertTrue(source.contains("Map<String, AiBusinessObject> objectLookupCache"));
        assertTrue(source.contains("context.businessObject() == null"));
        assertTrue(source.contains("businessObjectConverter.apply(context.businessObject())"));
    }

    @Test
    void documentFlowStartMustReusePreloadedValidationAndLatestLink() throws IOException {
        String source = Files.readString(resolveSource(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowStartCoordinator.java"));
        String method = method(source, "private BusinessFlowRuntimeVO startLocked", "    private void validateRequest", 1);

        assertEquals(1, countOccurrences(method, "flowInstanceLinkMapper.selectLatestByBusinessKey"));
        assertFalse(method.contains("flowInstanceLinkMapper.selectRunningByBusinessKey"));
        assertTrue(source.contains("documentConfigService.toVO("));
        assertTrue(source.contains("context.documentConfig(), context.runtimeConfig(), binding"));
        assertTrue(source.contains("documentRuntimeService.validateStartAllowed("));
        assertTrue(method.contains("resolveFlowBusinessKeyForStart(businessKey, latestLink)"));
        assertTrue(source.contains("resolveNextRoundNo(latestLink)"));
        assertTrue(method.contains("BusinessFlowBindingCodec.ensureBusinessBinding("));
        assertTrue(method.contains("bindingConfig, context.runtimeConfig(), context.documentConfig())"));
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

    private int countOccurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
