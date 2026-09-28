package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePublishTaskDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryDispatcherCadenceTest {

    @Test
    void recoveryOnlyDispatchersDoNotPollTheDatabaseEveryFewSeconds() throws Exception {
        assertFixedDelay(BusinessFlowCallbackInboxDispatcher.class, "recover",
                "${forge.business.flow-callback-inbox.scan-interval-ms:30000}");
        assertFixedDelay(BusinessFlowRemoteCommandDispatcher.class, "recover",
                "${forge.business.flow-remote-command.scan-interval-ms:30000}");
        assertFixedDelay(BusinessFlowStatusReconciliationDispatcher.class, "reconcile",
                "${forge.business.flow-status-sync.scan-interval-ms:60000}");
        assertFixedDelay(BusinessTriggerRecoveryDispatcher.class, "recoverPendingExecutions",
                "${forge.business.trigger-recovery.scan-interval-ms:60000}");
        assertFixedDelay(LowcodePublishTaskDispatcher.class, "dispatch",
                "${forge.lowcode.publish-task.scan-interval-ms:30000}");
    }

    private void assertFixedDelay(Class<?> type, String methodName, String expected) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo(expected);
    }
}
