package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Pure deep-link and card-content rendering shared by todo, result and CC notifications. */
final class FlowNotificationContentRenderer {

    private static final String DEFAULT_TODO_DETAIL_PATH = "/#/pages/todo-detail?taskId={taskId}";

    private FlowNotificationContentRenderer() {
    }

    static String buildH5TodoDetailUrl(String h5BaseUrl, FlowTask flowTask, FlowBusiness business, FlowModel model) {
        String template = resolveTodoDetailTemplate(model);
        return appendH5BasePath(h5BaseUrl, renderUrlTemplate(template, flowTask, business));
    }

    private static String resolveTodoDetailTemplate(FlowModel model) {
        if (model != null && model.getTodoDetailUrlTemplate() != null
                && !model.getTodoDetailUrlTemplate().isBlank()) {
            return model.getTodoDetailUrlTemplate().trim();
        }
        return DEFAULT_TODO_DETAIL_PATH;
    }

    static String appendH5BasePath(String h5BaseUrl, String rendered) {
        if (isHttpUrl(rendered)) {
            return rendered;
        }
        String base = h5BaseUrl == null ? "" : h5BaseUrl.trim();
        int hashIndex = base.indexOf('#');
        if (hashIndex >= 0) {
            base = base.substring(0, hashIndex);
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "";
        }
        String path = rendered.startsWith("/") ? rendered : "/" + rendered;
        return base + path;
    }

    static String renderUrlTemplate(String template, FlowTask flowTask, FlowBusiness business) {
        String taskId = flowTask == null ? "" : safeText(flowTask.getTaskId(), "");
        String processInstanceId = flowTask == null ? "" : safeText(flowTask.getProcessInstanceId(), "");
        String businessKey = business == null ? "" : safeText(business.getBusinessKey(), "");
        return template
                .replace("{taskId}", urlEncode(taskId))
                .replace("{businessKey}", urlEncode(businessKey))
                .replace("{processInstanceId}", urlEncode(processInstanceId));
    }

    private static String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String normalizeTemplatePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "";
        }
        String normalized = platform.trim().toUpperCase();
        return "WECHAT_ENTERPRISE".equals(normalized) ? "WECOM" : normalized;
    }

    static String buildDefaultCardDescription(String taskTitle, String processName, String startUserName) {
        StringBuilder description = new StringBuilder();
        description.append("<div class=\"gray\">流程待办提醒</div>");
        description.append("<div class=\"normal\">任务：").append(taskTitle).append("</div>");
        if (processName != null && !processName.isBlank()) {
            description.append("<div class=\"normal\">流程：").append(processName).append("</div>");
        }
        if (startUserName != null && !startUserName.isBlank()) {
            description.append("<div class=\"normal\">发起人：").append(startUserName).append("</div>");
        }
        description.append("<div class=\"highlight\">点击卡片查看详情并办理 ›</div>");
        return description.toString();
    }

    static String buildDefaultResultCardDescription(String processName, String resultText, String applyUserName) {
        StringBuilder description = new StringBuilder();
        description.append("<div class=\"gray\">流程审批结果通知</div>");
        description.append("<div class=\"normal\">流程：").append(processName).append("</div>");
        description.append("<div class=\"normal\">结果：").append(resultText).append("</div>");
        if (applyUserName != null && !applyUserName.isBlank()) {
            description.append("<div class=\"normal\">发起人：").append(applyUserName).append("</div>");
        }
        description.append("<div class=\"highlight\">点击卡片查看详情 ›</div>");
        return description.toString();
    }

    static String buildDefaultCcCardDescription(String processName) {
        StringBuilder description = new StringBuilder();
        description.append("<div class=\"gray\">流程抄送通知</div>");
        description.append("<div class=\"normal\">流程：").append(processName).append("</div>");
        description.append("<div class=\"highlight\">点击卡片查看详情 ›</div>");
        return description.toString();
    }

    static boolean isHttpUrl(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Escape card fields after truncation so user content cannot create markup. */
    static String cardText(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars) + "…";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
