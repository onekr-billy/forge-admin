package com.mdframe.forge.plugin.generator.service.lowcode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodePublishReliableTaskContractTest {

    @Test
    void publishAndRollbackAppendDurableTaskInsteadOfBestEffortAsyncEvent() throws Exception {
        String source = Files.readString(resolveSource("LowcodePublishService.java"), StandardCharsets.UTF_8);

        assertTrue(count(source, "publishTaskService.appendPostSync(") >= 2);
        assertTrue(source.contains("OPERATION_PUBLISH"));
        assertTrue(source.contains("OPERATION_ROLLBACK"));
        assertFalse(source.contains("ApplicationEventPublisher"));
        assertFalse(source.contains("LowcodePublishPostEvent"));
    }

    @Test
    void postActionFencesOlderVersionBeforeMenuOrEntrySideEffects() throws Exception {
        String source = Files.readString(
                resolveSource("LowcodePublishPostActionService.java"), StandardCharsets.UTF_8);

        int versionFence = source.indexOf(
                "!Objects.equals(command.versionNo(), config.getPublishedVersion())");
        int snapshotLoad = source.indexOf("loadPublishedSnapshot(command)");
        int menuSideEffect = source.indexOf("registerOrUpdateMenu(", snapshotLoad);
        int entrySideEffect = source.indexOf("syncBusinessRuntimeEntry(config");
        assertTrue(versionFence > 0);
        assertTrue(snapshotLoad > versionFence);
        assertTrue(menuSideEffect > snapshotLoad);
        assertTrue(entrySideEffect > snapshotLoad);
    }

    @Test
    void dispatcherRequiresAdminPublishSynchronizationCapabilityBeforeClaim() throws Exception {
        String source = Files.readString(
                resolveSource("LowcodePublishTaskDispatcher.java"), StandardCharsets.UTF_8);

        int capabilityGuard = source.indexOf("if (!actionService.supportsExecution())");
        int candidateScan = source.indexOf("taskService.findCandidates(");
        int claim = source.indexOf("taskService.claim(");
        assertTrue(capabilityGuard > 0);
        assertTrue(candidateScan > capabilityGuard);
        assertTrue(claim > capabilityGuard);
    }

    @Test
    void onlineDdlPublishIsStagedInsteadOfExecutedInsidePublishTransaction() throws Exception {
        String source = Files.readString(resolveSource("LowcodePublishService.java"), StandardCharsets.UTF_8);

        int onlinePlan = source.indexOf("new LowcodeOnlinePublishPlan(");
        int stagedPublish = source.indexOf("onlinePublishCoordinator.publish(plan)");
        assertTrue(onlinePlan > 0);
        assertTrue(stagedPublish > onlinePlan);
        assertFalse(source.contains("ddlService.executeCreateTable(modelSchema)"));
    }

    private int count(String source, String token) {
        int count = 0;
        int position = 0;
        while ((position = source.indexOf(token, position)) >= 0) {
            count++;
            position += token.length();
        }
        return count;
    }

    private Path resolveSource(String fileName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/"
                            + "src/main/java/com/mdframe/forge/plugin/generator/service/lowcode/" + fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "forge-framework/forge-plugin-parent/forge-plugin-generator/"
                            + "src/main/java/com/mdframe/forge/plugin/generator/service/lowcode/" + fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of("src/main/java/com/mdframe/forge/plugin/generator/service/lowcode").resolve(fileName);
    }
}
