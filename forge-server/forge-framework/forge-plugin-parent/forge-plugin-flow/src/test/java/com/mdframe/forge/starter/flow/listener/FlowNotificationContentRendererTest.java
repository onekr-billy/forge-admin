package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowNotificationContentRendererTest {

    @Test
    void defaultTodoLinkRemovesExistingHashAndEncodesTaskId() {
        FlowTask task = new FlowTask();
        task.setTaskId("task 1&2");

        assertEquals("https://example.com/forge-h5/#/pages/todo-detail?taskId=task+1%262",
                FlowNotificationContentRenderer.buildH5TodoDetailUrl(
                        "https://example.com/forge-h5/#/old", task, null, null));
    }

    @Test
    void absoluteTemplateKeepsItsHostAndEncodesAllVariables() {
        FlowTask task = new FlowTask();
        task.setTaskId("task 1");
        task.setProcessInstanceId("flow/2");
        FlowBusiness business = new FlowBusiness();
        business.setBusinessKey("order:1&2");
        FlowModel model = new FlowModel();
        model.setTodoDetailUrlTemplate(
                "https://flow.example/view?task={taskId}&key={businessKey}&process={processInstanceId}");

        assertEquals("https://flow.example/view?task=task+1&key=order%3A1%262&process=flow%2F2",
                FlowNotificationContentRenderer.buildH5TodoDetailUrl(
                        "https://ignored.example/#/", task, business, model));
        assertEquals("https://example.com/base/relative/path",
                FlowNotificationContentRenderer.appendH5BasePath(
                        "https://example.com/base/#/old", "relative/path"));
    }

    @Test
    void cardTextTruncatesThenEscapesAndKeepsFallbackDescriptions() {
        assertEquals("&lt;&amp;…", FlowNotificationContentRenderer.cardText(" <&abc ", 2));
        assertEquals("", FlowNotificationContentRenderer.cardText(null, 10));
        assertEquals("备用", FlowNotificationContentRenderer.safeText(" ", "备用"));
        assertEquals("WECOM", FlowNotificationContentRenderer.normalizeTemplatePlatform(" wechat_enterprise "));
        assertTrue(FlowNotificationContentRenderer.buildDefaultCardDescription("任务", "流程", "发起人")
                .contains("点击卡片查看详情并办理"));
        assertTrue(FlowNotificationContentRenderer.buildDefaultResultCardDescription("流程", "通过", "发起人")
                .contains("结果：通过"));
        assertTrue(FlowNotificationContentRenderer.buildDefaultCcCardDescription("流程")
                .contains("流程抄送通知"));
        assertFalse(FlowNotificationContentRenderer.isHttpUrl("/#/pages/todo"));
    }

    @Test
    void dispatcherHandlesNotificationsAfterCommitWithoutFallbackExecution() throws Exception {
        var method = FlowNotifyOutboxDispatcher.class.getMethod("onNotifyEvent", FlowTaskNotifyEvent.class);
        Async async = method.getAnnotation(Async.class);
        TransactionalEventListener transactional = method.getAnnotation(TransactionalEventListener.class);

        assertNotNull(async);
        assertEquals("flowEventExecutor", async.value());
        assertNotNull(transactional);
        assertEquals(TransactionPhase.AFTER_COMMIT, transactional.phase());
        assertFalse(transactional.fallbackExecution());
    }
}
