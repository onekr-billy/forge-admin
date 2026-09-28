package com.mdframe.forge.flow.client.helper;

import com.mdframe.forge.flow.client.annotation.FlowBind;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowEventSubscriberReliableDispatchTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void reliableDispatchPropagatesCallbackFailureSoStreamRemainsPending() {
        try (AnnotationConfigApplicationContext context = contextWith(FailingHandler.class)) {
            FlowEventSubscriber subscriber = new FlowEventSubscriber(context);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> subscriber.dispatchReliable(event()));

            assertEquals("FLOW_CALLBACK_DISPATCH_FAILED", failure.getMessage());
        }
    }

    @Test
    void legacyDispatchKeepsCompatibilityAndSwallowsCallbackFailure() {
        try (AnnotationConfigApplicationContext context = contextWith(FailingHandler.class)) {
            FlowEventSubscriber subscriber = new FlowEventSubscriber(context);

            assertDoesNotThrow(() -> subscriber.dispatch(event()));
        }
    }

    @Test
    void reliableMessageRejectsInvalidJsonInsteadOfAcknowledgingIt() {
        try (AnnotationConfigApplicationContext context = contextWith(SuccessHandler.class)) {
            FlowEventSubscriber subscriber = new FlowEventSubscriber(context);

            assertThrows(IllegalArgumentException.class,
                    () -> subscriber.onMessageReliable("{invalid-json"));
        }
    }

    @Test
    void reliableMessageRejectsMissingTenantAndEventIdentity() {
        try (AnnotationConfigApplicationContext context = contextWith(SuccessHandler.class)) {
            FlowEventSubscriber subscriber = new FlowEventSubscriber(context);

            assertThrows(IllegalArgumentException.class,
                    () -> subscriber.onMessageReliable("{\"event\":\"PROCESS_COMPLETED\","
                            + "\"processDefKey\":\"reliable-flow\",\"businessKey\":\"order:42\"}"));
        }
    }

    private AnnotationConfigApplicationContext contextWith(Class<?> handlerType) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(handlerType);
        context.refresh();
        return context;
    }

    private FlowEventContext event() {
        FlowEventContext event = new FlowEventContext();
        event.setTenantId(7L);
        event.setEvent(FlowCallback.ON_COMPLETED);
        event.setProcessDefKey("reliable-flow");
        event.setBusinessKey("order:42");
        return event;
    }

    @FlowBind(modelKey = "reliable-flow")
    static class FailingHandler {

        @FlowCallback(on = FlowCallback.ON_COMPLETED)
        public void handle(FlowEventContext ignored) {
            throw new IllegalStateException("sensitive callback failure");
        }
    }

    @FlowBind(modelKey = "reliable-flow")
    static class SuccessHandler {

        @FlowCallback(on = FlowCallback.ON_COMPLETED)
        public void handle(FlowEventContext ignored) {
            // successful callback
        }
    }
}
