package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.event.FlowEventPublisher;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.event.FlowWebhookNotifier;
import com.mdframe.forge.starter.flow.mapper.FlowModelMapper;
import com.mdframe.forge.starter.flow.service.FlowCcService;
import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import com.mdframe.forge.starter.flow.service.FlowTaskReceiverResolver;
import com.mdframe.forge.starter.flow.support.FlowNotifyConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程通知异步监听器
 *
 * <p>消费 {@link FlowTaskNotifyEvent}：站内信推送、企微待办卡片、待办置已读、
 * 流程抄送、Redis/Webhook 事件通知。使用 {@code AFTER_COMMIT} 保证审批事务
 * 提交后才发出通知（事务回滚不发），并通过 {@code flowEventExecutor} 线程池
 * 异步执行，外部 HTTP 调用不再阻塞审批主链路。</p>
 *
 * <p>消息/协同模块的依赖收敛在本类，{@code FlowTaskEventListener} 只负责
 * 引擎事件到业务表的数据同步。</p>
 */
@Slf4j
@Component
public class FlowTaskNotifyListener {

    private static final String FLOW_TODO_MESSAGE_BIZ_TYPE = "FLOW_TODO";

    /** 审批结果通知消息业务类型 */
    private static final String FLOW_RESULT_MESSAGE_BIZ_TYPE = "FLOW_RESULT";

    /** 流程抄送渠道通知消息业务类型 */
    private static final String FLOW_CC_MESSAGE_BIZ_TYPE = "FLOW_CC";

    /** 默认待办详情深链相对路径（流程模型未配置 todoDetailUrlTemplate 时使用） */
    private static final String DEFAULT_TODO_DETAIL_PATH = "/#/pages/todo-detail?taskId={taskId}";

    /** 默认审批结果卡片落地页（流程模型未配置 todoDetailUrlTemplate 时使用，跳转 H5 待办列表） */
    private static final String DEFAULT_RESULT_DETAIL_PATH = "/#/pages/todo";

    /** 待办卡片通用消息模板编码，平台差异化模板为 {@code FLOW_TODO_CARD_平台} */
    private static final String DEFAULT_TODO_CARD_TEMPLATE = "FLOW_TODO_CARD";

    /** 审批结果卡片通用消息模板编码，平台差异化模板为 {@code FLOW_RESULT_CARD_平台} */
    private static final String DEFAULT_RESULT_CARD_TEMPLATE = "FLOW_RESULT_CARD";

    /** 抄送卡片通用消息模板编码，平台差异化模板为 {@code FLOW_CC_CARD_平台} */
    private static final String DEFAULT_CC_CARD_TEMPLATE = "FLOW_CC_CARD";

    @Autowired(required = false)
    @Lazy
    private MessageService messageService;

    @Autowired(required = false)
    @Lazy
    private FlowTaskReceiverResolver taskReceiverResolver;

    /** 企业协同连接配置服务，待办卡片推送配置收敛在连接管理（sys_social_config） */
    @Autowired(required = false)
    @Lazy
    private ISocialConfigService socialConfigService;

    @Autowired(required = false)
    @Lazy
    private FlowCcService flowCcService;

    @Autowired(required = false)
    @Lazy
    private FlowOrgIntegrationService flowOrgIntegrationService;

    @Autowired
    @Lazy
    private FlowModelMapper flowModelMapper;

    /** Redis Pub/Sub 发布器（可选，未引入 Redis 依赖时为 null）*/
    @Autowired(required = false)
    @Lazy
    private FlowEventPublisher flowEventPublisher;

    /** HTTP Webhook 回调器 */
    @Autowired
    @Lazy
    private FlowWebhookNotifier flowWebhookNotifier;

    /**
     * 事务提交后异步消费通知事件；无事务上下文时（fallbackExecution）直接异步执行
     */
    @Async("flowEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotifyEvent(FlowTaskNotifyEvent event) {
        try {
            switch (event.getType()) {
                case TASK_TODO:
                    sendTaskCreatedMessage(event.getFlowTask(), event.getBusiness(), event.getVariables());
                    break;
                case TASK_TODO_READ:
                    markTaskTodoMessageRead(event.getTaskId(), event.getBusiness());
                    break;
                case PROCESS_CC:
                    sendProcessCc(event.getBusiness(), event.getVariables());
                    break;
                case PROCESS_RESULT:
                    sendProcessResult(event.getBusiness(), event.getVariables(),
                            Boolean.TRUE.equals(event.getRejected()));
                    break;
                case EVENT_PUBLISH:
                    publishEvent(event.getEventMessage(), event.getProcessDefKey());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.warn("流程通知异步处理失败，不影响主流程: type={}", event.getType(), e);
        }
    }

    /**
     * 待办创建/分配通知：优先按流程模型 notify_config 的 todo 事件渠道矩阵分发
     * （WEB/EMAIL/SMS/COLLABORATION）；未配置时保持默认行为（站内信 + 连接开启
     * 待办推送时推企微卡片），保证升级零回归。
     */
    private void sendTaskCreatedMessage(FlowTask flowTask, FlowBusiness business, Map<String, Object> variables) {
        if (messageService == null || flowTask == null || flowTask.getTaskId() == null) {
            return;
        }
        if (taskReceiverResolver == null) {
            log.warn("待办站内信接收人解析器未初始化: taskId={}", flowTask.getTaskId());
            return;
        }
        // 事务提交后的异步线程没有继承请求租户上下文；接收人解析可能读取
        // sys_user/sys_role 等租户表，因此显式恢复业务租户。
        Set<Long> receiverIds = runWithBusinessTenantResult(business,
                () -> taskReceiverResolver.resolveReceivers(flowTask));
        if (receiverIds.isEmpty()) {
            log.warn("待办任务没有可推送的站内信接收人: taskId={}, assignee={}, candidateUsers={}, candidateGroups={}",
                    flowTask.getTaskId(), flowTask.getAssignee(), flowTask.getCandidateUsers(), flowTask.getCandidateGroups());
            return;
        }

        FlowModel model = loadFlowModel(business);
        FlowNotifyConfig notifyConfig = FlowNotifyConfig.parse(model == null ? null : model.getNotifyConfig());
        FlowNotifyConfig.ChannelConfig todoConfig = notifyConfig == null
                ? null : notifyConfig.channelConfigOf(FlowNotifyConfig.EVENT_TODO);

        if (todoConfig == null) {
            // 未配置通知矩阵：默认行为（站内信 + 企微卡片跟随连接开关）
            sendTaskWebMessage(flowTask, business, receiverIds, null, null);
            sendTaskCollaborationCard(flowTask, business, receiverIds, model, null, null, true);
            return;
        }

        for (String channel : todoConfig.getChannels()) {
            String normalized = channel == null ? "" : channel.trim().toUpperCase();
            try {
                switch (normalized) {
                    case FlowNotifyConfig.CHANNEL_WEB:
                        sendTaskWebMessage(flowTask, business, receiverIds, todoConfig.getTemplateCode(), variables);
                        break;
                    case FlowNotifyConfig.CHANNEL_EMAIL:
                    case FlowNotifyConfig.CHANNEL_SMS:
                        sendTaskChannelMessage(flowTask, business, receiverIds,
                                normalized, todoConfig.getTemplateCode(), variables);
                        break;
                    case FlowNotifyConfig.CHANNEL_COLLABORATION:
                        sendTaskCollaborationCard(flowTask, business, receiverIds,
                                model, todoConfig.getTemplateCode(), variables, false);
                        break;
                    default:
                        log.warn("未知的待办通知渠道，跳过: taskId={}, channel={}", flowTask.getTaskId(), channel);
                }
            } catch (Exception e) {
                // 单渠道失败不影响其他渠道
                log.warn("待办通知渠道推送失败，不影响其他渠道: taskId={}, channel={}",
                        flowTask.getTaskId(), channel, e);
            }
        }
    }

    /**
     * 待办站内信（WEB 渠道）
     */
    private void sendTaskWebMessage(FlowTask flowTask, FlowBusiness business, Set<Long> receiverIds,
                                    String templateCode, Map<String, Object> variables) {
        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTitle("您有新的流程待办");
        request.setContent("您有一个待办任务需要处理：" + safeText(flowTask.getTitle(), flowTask.getTaskName()));
        request.setType("SYSTEM");
        request.setChannel(FlowNotifyConfig.CHANNEL_WEB);
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        Map<String, Object> params = baseTaskParams(flowTask);
        mergeVariables(params, variables);
        request.setParams(params);
        if (templateCode != null && !templateCode.isBlank()) {
            request.setTemplateCode(templateCode.trim());
        }
        try {
            runWithBusinessTenant(business,
                    () -> messageService.sendIfAbsent(request, FLOW_TODO_MESSAGE_BIZ_TYPE, flowTask.getTaskId()));
            log.info("待办站内信已推送: taskId={}, receivers={}", flowTask.getTaskId(), receiverIds);
        } catch (Exception e) {
            log.warn("待办站内信推送失败，不阻断流程: taskId={}", flowTask.getTaskId(), e);
        }
    }

    /**
     * 待办邮件/短信渠道通知（走消息中心统一渠道）
     */
    private void sendTaskChannelMessage(FlowTask flowTask, FlowBusiness business, Set<Long> receiverIds,
                                        String channel, String templateCode, Map<String, Object> variables) {
        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTitle("您有新的流程待办");
        request.setContent("您有一个待办任务需要处理：" + safeText(flowTask.getTitle(), flowTask.getTaskName()));
        request.setType("SYSTEM");
        request.setChannel(channel);
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        Map<String, Object> params = baseTaskParams(flowTask);
        mergeVariables(params, variables);
        request.setParams(params);
        if (templateCode != null && !templateCode.isBlank()) {
            request.setTemplateCode(templateCode.trim());
        }
        try {
            runWithBusinessTenant(business,
                    () -> messageService.sendIfAbsent(request, FLOW_TODO_MESSAGE_BIZ_TYPE,
                            flowTask.getTaskId() + ":" + channel));
            log.info("待办{}通知已推送: taskId={}, receivers={}", channel, flowTask.getTaskId(), receiverIds);
        } catch (Exception e) {
            log.warn("待办{}通知推送失败，不阻断流程: taskId={}", channel, flowTask.getTaskId(), e);
        }
    }

    /** 待办消息基础模板变量 */
    private Map<String, Object> baseTaskParams(FlowTask flowTask) {
        Map<String, Object> params = new HashMap<>();
        params.put("taskId", flowTask.getTaskId());
        params.put("processInstanceId", safeText(flowTask.getProcessInstanceId(), ""));
        params.put("jumpUrl", "/flow/todo?taskId=" + flowTask.getTaskId());
        return params;
    }

    /**
     * 流程变量注入模板参数（仅补充不存在的 key，避免覆盖基础占位符）
     */
    private void mergeVariables(Map<String, Object> params, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        variables.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !params.containsKey(key)) {
                params.put(key, value);
            }
        });
    }

    /**
     * 待办推送企业协同卡片消息（企微 textcard），卡片点击跳转 H5 待办详情。
     * 推送开关与 H5 地址配置在企业连接管理（sys_social_config），未启用时静默跳过；失败不阻断流程。
     * 模型通知配置了 todo 事件 COLLABORATION 渠道时强制推送（不再依赖连接开关）；
     * templateCodeOverride 支持模型级模板编码覆盖。
     */
    private void sendTaskCollaborationCard(FlowTask flowTask, FlowBusiness business, Set<Long> receiverIds,
                                           FlowModel model, String templateCodeOverride, Map<String, Object> variables,
                                           boolean requireConnectionSwitch) {
        if (socialConfigService == null) {
            return;
        }
        SysSocialConfig connection;
        try {
            connection = runWithBusinessTenantResult(business, () -> resolveTodoPushConnection(requireConnectionSwitch));
        } catch (Exception e) {
            log.warn("解析待办推送连接配置失败，跳过卡片推送: taskId={}", flowTask.getTaskId(), e);
            return;
        }
        if (connection == null) {
            return;
        }
        String detailUrl = buildH5TodoDetailUrl(connection.getTodoPushH5Url(), flowTask, business, model);
        // 地址非法时企微渠道会整批拒绝（TEMPLATE_INVALID），在源头拦下并指明是连接配置问题
        if (!isHttpUrl(detailUrl)) {
            log.warn("待办H5访问地址不是合法的http/https地址，跳过卡片推送: connectionId={}, taskId={}, h5Url={}",
                    connection.getId(), flowTask.getTaskId(), connection.getTodoPushH5Url());
            return;
        }

        // 卡片字段值统一转义并截断（企微 textcard.description 仅支持有限 HTML），整体控制在 512 字节内
        String taskTitle = cardText(safeText(flowTask.getTitle(), flowTask.getTaskName()), 60);
        String processName = flowTask.getProcessName() == null ? "" : cardText(flowTask.getProcessName(), 40);
        String startUserName = flowTask.getStartUserName() == null ? "" : cardText(flowTask.getStartUserName(), 20);

        Map<String, Object> params = new HashMap<>();
        params.put("msgType", "textcard");
        params.put("url", detailUrl);
        params.put("taskId", flowTask.getTaskId());
        params.put("processInstanceId", safeText(flowTask.getProcessInstanceId(), ""));
        params.put("taskTitle", taskTitle);
        params.put("processName", processName);
        params.put("startUserName", startUserName);
        mergeVariables(params, variables);

        sendCollaborationCardMessage(business, connection, receiverIds, params,
                "您有新的流程待办", buildDefaultCardDescription(taskTitle, processName, startUserName),
                DEFAULT_TODO_CARD_TEMPLATE, templateCodeOverride,
                FLOW_TODO_MESSAGE_BIZ_TYPE, flowTask.getTaskId() + ":COLLABORATION", detailUrl);
    }

    /**
     * 企业协同卡片统一发送（待办/结果/抄送共用）：模板优先级为
     * 模型覆盖编码 → 平台差异化默认编码 → 通用默认编码 → 内置兜底排版。
     */
    private void sendCollaborationCardMessage(FlowBusiness business, SysSocialConfig connection,
                                              Set<Long> receiverIds, Map<String, Object> params,
                                              String fallbackTitle, String fallbackContent,
                                              String defaultTemplateCode, String templateCodeOverride,
                                              String bizType, String bizKey, String detailUrl) {
        if (messageService == null) {
            return;
        }
        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setType("SYSTEM");
        request.setChannel(FlowNotifyConfig.CHANNEL_COLLABORATION);
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        request.setConnectionId(connection.getId());
        request.setParams(params);

        // 模板查询受租户拦截器保护，必须在业务租户上下文中解析；此方法由异步线程调用，
        // 不能假设审批请求的 ThreadLocal 租户上下文仍然存在。
        String templateCode = runWithBusinessTenantResult(business,
                () -> resolveCardTemplateCode(connection.getPlatform(), defaultTemplateCode, templateCodeOverride));
        if (templateCode != null) {
            request.setTemplateCode(templateCode);
        } else {
            request.setTitle(fallbackTitle);
            request.setContent(fallbackContent);
        }
        try {
            SysMessage message = runWithBusinessTenantResult(business,
                    () -> messageService.sendIfAbsent(request, bizType, bizKey));
            // 逐人投递失败不抛异常，只体现在消息状态上，这里按结果打日志避免失败也报「已推送」
            if (message != null && Integer.valueOf(2).equals(message.getStatus())) {
                log.warn("企业协同卡片全部接收人投递失败，失败码见 sys_message_receiver.last_error_code: "
                                + "bizKey={}, messageId={}, receivers={}, url={}",
                        bizKey, message.getId(), receiverIds, detailUrl);
            } else {
                log.info("企业协同卡片已推送: bizKey={}, receivers={}, url={}", bizKey, receiverIds, detailUrl);
            }
        } catch (Exception e) {
            log.warn("企业协同卡片推送失败，不阻断流程: bizKey={}", bizKey, e);
        }
    }

    /**
     * 解析当前租户下可用的企业协同连接（须已配置 H5 地址）；多个时取第一个。
     *
     * @param requireTodoPushEnabled true 时还要求连接开启了待办卡片推送开关（默认行为，
     *                               未配置通知矩阵的流程沿用）；false 时模型通知矩阵已显式
     *                               勾选协同渠道，不再受连接开关限制，仅要求 H5 地址可用。
     */
    private SysSocialConfig resolveTodoPushConnection(boolean requireTodoPushEnabled) {
        SysSocialConfig query = new SysSocialConfig();
        query.setStatus(1);
        List<SysSocialConfig> candidates = socialConfigService.selectConfigList(query).stream()
                .filter(conn -> !requireTodoPushEnabled
                        || (conn.getTodoPushEnabled() != null && conn.getTodoPushEnabled() == 1))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<SysSocialConfig> usable = candidates.stream()
                .filter(conn -> conn.getTodoPushH5Url() != null && !conn.getTodoPushH5Url().isBlank())
                .toList();
        if (usable.isEmpty()) {
            log.warn("连接已启用待办卡片推送但未配置 H5 访问地址，跳过: connectionIds={}",
                    candidates.stream().map(SysSocialConfig::getId).toList());
            return null;
        }
        if (usable.size() > 1) {
            log.warn("租户存在多个启用待办推送的连接，默认选用第一个: connectionIds={}",
                    usable.stream().map(SysSocialConfig::getId).toList());
        }
        return usable.get(0);
    }

    /**
     * 拼接 H5 待办详情深链（hash 路由）。默认跳转全局待办详情页，流程模型可通过
     * {@link FlowModel#getTodoDetailUrlTemplate()} 覆盖为业务自定义路径（需求4）。
     * <p>模板支持占位符 {@code {taskId}}/{@code {businessKey}}/{@code {processInstanceId}}（自动 URL 编码）；
     * 模板可填相对路径（自动拼接连接的 H5 域名）或完整 http/https 地址。</p>
     * <p>配置地址常见直接从浏览器地址栏复制（形如 {@code http://host/forge-h5/#/}），
     * 因此先剥掉已有的 hash 片段再拼接，避免拼出两个 {@code #} 构成非法 URI 被模板校验整批拒绝。</p>
     */
    private String buildH5TodoDetailUrl(String h5BaseUrl, FlowTask flowTask, FlowBusiness business, FlowModel model) {
        String template = resolveTodoDetailTemplate(model);
        return appendH5BasePath(h5BaseUrl, renderUrlTemplate(template, flowTask, business));
    }

    /**
     * 解析流程模型配置的待办深链模板；未配置时回退全局默认待办详情页。
     */
    private String resolveTodoDetailTemplate(FlowModel model) {
        if (model != null && model.getTodoDetailUrlTemplate() != null
                && !model.getTodoDetailUrlTemplate().isBlank()) {
            return model.getTodoDetailUrlTemplate().trim();
        }
        return DEFAULT_TODO_DETAIL_PATH;
    }

    /**
     * 按流程定义 Key 加载流程模型（通知配置 + 深链模板复用），查询失败返回 null
     */
    private FlowModel loadFlowModel(FlowBusiness business) {
        if (business == null || business.getProcessDefKey() == null || flowModelMapper == null) {
            return null;
        }
        try {
            Long tenantId = business.getTenantId();
            if (tenantId != null && tenantId > 0) {
                return TenantContextHolder.executeWithTenant(tenantId,
                        () -> flowModelMapper.selectByModelKey(business.getProcessDefKey()));
            }
            return flowModelMapper.selectByModelKey(business.getProcessDefKey());
        } catch (Exception e) {
            log.debug("查询流程模型失败: processDefKey={}", business.getProcessDefKey(), e);
            return null;
        }
    }

    /**
     * 相对路径拼接连接的 H5 域名（剥掉已有 hash 片段），完整 http/https 地址原样返回
     */
    private String appendH5BasePath(String h5BaseUrl, String rendered) {
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

    /**
     * 渲染深链模板占位符，占位符值统一做 URL 编码避免破坏查询串。
     */
    private String renderUrlTemplate(String template, FlowTask flowTask, FlowBusiness business) {
        String taskId = flowTask == null ? "" : safeText(flowTask.getTaskId(), "");
        String processInstanceId = flowTask == null ? "" : safeText(flowTask.getProcessInstanceId(), "");
        String businessKey = business == null ? "" : safeText(business.getBusinessKey(), "");
        return template
                .replace("{taskId}", urlEncode(taskId))
                .replace("{businessKey}", urlEncode(businessKey))
                .replace("{processInstanceId}", urlEncode(processInstanceId));
    }

    private String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 按连接平台解析启用的卡片模板编码：模型覆盖编码 → 平台差异化默认编码 → 通用默认编码；
     * 均未启用时返回 null（调用方回退内置排版）。
     */
    private String resolveCardTemplateCode(String platform, String defaultTemplateCode, String templateCodeOverride) {
        if (messageService == null) {
            return null;
        }
        String normalized = normalizeTemplatePlatform(platform);
        String platformCode = defaultTemplateCode + (normalized.isEmpty() ? "" : "_" + normalized);
        if (templateCodeOverride != null && !templateCodeOverride.isBlank()) {
            return messageService.resolveEnabledTemplateCode(templateCodeOverride.trim(), platformCode, defaultTemplateCode);
        }
        return messageService.resolveEnabledTemplateCode(platformCode, defaultTemplateCode);
    }

    private String normalizeTemplatePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "";
        }
        String normalized = platform.trim().toUpperCase();
        return "WECHAT_ENTERPRISE".equals(normalized) ? "WECOM" : normalized;
    }

    /**
     * 内置待办卡片排版（未配置启用模板时回退），字段值已在调用处转义。
     */
    private String buildDefaultCardDescription(String taskTitle, String processName, String startUserName) {
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

    /**
     * 校验是否为 http/https 地址；含多个 {@code #} 等非法字符时 URI 解析会抛错
     */
    private boolean isHttpUrl(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void markTaskTodoMessageRead(String taskId, FlowBusiness business) {
        if (messageService == null || taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            final int[] updated = {0};
            runWithBusinessTenant(business,
                    () -> updated[0] = messageService.markWebReadByBiz(FLOW_TODO_MESSAGE_BIZ_TYPE, taskId));
            if (updated[0] > 0) {
                log.info("待办站内信已自动置为已读: taskId={}, updated={}", taskId, updated[0]);
            }
        } catch (Exception e) {
            log.warn("待办站内信自动置已读失败，不阻断流程: taskId={}", taskId, e);
        }
    }

    private void sendProcessCc(FlowBusiness business, Map<String, Object> variables) {
        if (flowOrgIntegrationService == null || business == null || variables == null || variables.isEmpty()) {
            return;
        }
        List<String> roleKeys = resolveCcRoleKeys(variables.get("ccRoleKeys"));
        if (roleKeys.isEmpty()) {
            return;
        }

        Set<String> ccUserIds = runWithBusinessTenantResult(business, () -> {
            Set<String> resolvedUserIds = new LinkedHashSet<>();
            for (String roleKey : roleKeys) {
                try {
                    List<String> userIds = flowOrgIntegrationService.getUserIdsByRoleCode(roleKey);
                    if (userIds != null) {
                        resolvedUserIds.addAll(userIds);
                    }
                } catch (Exception e) {
                    log.warn("流程抄送角色解析失败: businessKey={}, roleKey={}",
                            business.getBusinessKey(), roleKey, e);
                }
            }
            return resolvedUserIds;
        });
        if (ccUserIds.isEmpty()) {
            log.warn("流程抄送未找到接收人: businessKey={}, roleKeys={}", business.getBusinessKey(), roleKeys);
            return;
        }

        List<String> userIds = new ArrayList<>(ccUserIds);
        if (flowCcService != null) {
            try {
                runWithBusinessTenant(business, () -> flowCcService.sendCc(
                        business.getProcessInstanceId(),
                        business.getProcessDefKey(),
                        null,
                        business.getTitle(),
                        "流程已通过，请知悉：" + safeText(business.getTitle(), business.getBusinessKey()),
                        business.getBusinessKey(),
                        userIds,
                        resolveUserNames(userIds),
                        business.getApplyUserId(),
                        business.getApplyUserName()));
            } catch (Exception e) {
                log.warn("流程抄送发送失败，不阻断主流程: businessKey={}, ccUserIds={}",
                        business.getBusinessKey(), userIds, e);
            }
        } else {
            log.warn("流程抄送落库服务未初始化，仅继续执行配置化渠道通知: businessKey={}",
                    business.getBusinessKey());
        }

        // 按模型通知配置向抄送人推送渠道消息（未配置 cc 事件时仅保留抄送记录，行为不变）
        sendCcChannelMessages(business, userIds, variables);
    }

    /**
     * 审批结果通知：按流程模型 notify_config 的 result 事件渠道矩阵向发起人推送
     * （通过/驳回），未配置时无动作（历史行为保持）。
     */
    private void sendProcessResult(FlowBusiness business, Map<String, Object> variables, boolean rejected) {
        if (messageService == null || business == null) {
            return;
        }
        FlowNotifyConfig.ChannelConfig resultConfig = resolveChannelConfig(business, FlowNotifyConfig.EVENT_RESULT);
        if (resultConfig == null) {
            return;
        }
        Long applyUserId = parseUserId(business.getApplyUserId());
        if (applyUserId == null) {
            log.warn("审批结果通知未找到发起人，跳过: businessKey={}, applyUserId={}",
                    business.getBusinessKey(), business.getApplyUserId());
            return;
        }
        Set<Long> receiverIds = Set.of(applyUserId);
        String resultText = rejected ? "已驳回" : "已审批通过";
        String title = rejected ? "流程审批结果：已驳回" : "流程审批结果：已通过";
        String content = "您提交的「" + safeText(business.getTitle(), business.getBusinessKey()) + "」"
                + resultText + "，请知悉。";
        String bizKey = safeText(business.getBusinessKey(), safeText(business.getProcessInstanceId(), "")) + ":RESULT";

        for (String channel : resultConfig.getChannels()) {
            String normalized = channel == null ? "" : channel.trim().toUpperCase();
            try {
                switch (normalized) {
                    case FlowNotifyConfig.CHANNEL_WEB:
                    case FlowNotifyConfig.CHANNEL_EMAIL:
                    case FlowNotifyConfig.CHANNEL_SMS:
                        sendResultChannelMessage(business, receiverIds, normalized,
                                resultConfig.getTemplateCode(), title, content, variables, bizKey);
                        break;
                    case FlowNotifyConfig.CHANNEL_COLLABORATION:
                        sendResultCollaborationCard(business, receiverIds,
                                resultConfig.getTemplateCode(), rejected, variables, bizKey);
                        break;
                    default:
                        log.warn("未知的审批结果通知渠道，跳过: businessKey={}, channel={}",
                                business.getBusinessKey(), channel);
                }
            } catch (Exception e) {
                log.warn("审批结果通知渠道推送失败，不影响其他渠道: businessKey={}, channel={}",
                        business.getBusinessKey(), channel, e);
            }
        }
    }

    /**
     * 审批结果站内信/邮件/短信渠道通知
     */
    private void sendResultChannelMessage(FlowBusiness business, Set<Long> receiverIds, String channel,
                                          String templateCode, String title, String content,
                                          Map<String, Object> variables, String bizKey) {
        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTitle(title);
        request.setContent(content);
        request.setType("SYSTEM");
        request.setChannel(channel);
        request.setSendScope("USERS");
        request.setUserIds(receiverIds);
        Map<String, Object> params = baseBusinessParams(business);
        params.put("result", content);
        mergeVariables(params, variables);
        request.setParams(params);
        if (templateCode != null && !templateCode.isBlank()) {
            request.setTemplateCode(templateCode.trim());
        }
        try {
            runWithBusinessTenant(business,
                    () -> messageService.sendIfAbsent(request, FLOW_RESULT_MESSAGE_BIZ_TYPE, bizKey + ":" + channel));
            log.info("审批结果{}通知已推送: businessKey={}, receivers={}", channel, business.getBusinessKey(), receiverIds);
        } catch (Exception e) {
            log.warn("审批结果{}通知推送失败，不阻断流程: businessKey={}", channel, business.getBusinessKey(), e);
        }
    }

    /**
     * 审批结果企业协同卡片：跳转模型深链模板（未配置时跳 H5 待办列表）
     */
    private void sendResultCollaborationCard(FlowBusiness business, Set<Long> receiverIds,
                                             String templateCodeOverride, boolean rejected,
                                             Map<String, Object> variables, String bizKey) {
        if (socialConfigService == null) {
            return;
        }
        SysSocialConfig connection;
        try {
            connection = runWithBusinessTenantResult(business, () -> resolveTodoPushConnection(false));
        } catch (Exception e) {
            log.warn("解析审批结果推送连接配置失败，跳过卡片推送: businessKey={}", business.getBusinessKey(), e);
            return;
        }
        if (connection == null) {
            return;
        }
        FlowModel model = loadFlowModel(business);
        String template = model != null && model.getTodoDetailUrlTemplate() != null
                && !model.getTodoDetailUrlTemplate().isBlank()
                ? model.getTodoDetailUrlTemplate().trim() : DEFAULT_RESULT_DETAIL_PATH;
        String detailUrl = appendH5BasePath(connection.getTodoPushH5Url(),
                renderUrlTemplate(template, null, business));
        if (!isHttpUrl(detailUrl)) {
            log.warn("审批结果H5访问地址不是合法的http/https地址，跳过卡片推送: connectionId={}, h5Url={}",
                    connection.getId(), connection.getTodoPushH5Url());
            return;
        }

        String processName = cardText(safeText(business.getTitle(), business.getBusinessKey()), 60);
        String applyUserName = cardText(safeText(business.getApplyUserName(), ""), 20);
        String resultText = rejected ? "已驳回" : "已通过";

        Map<String, Object> params = new HashMap<>();
        params.put("msgType", "textcard");
        params.put("url", detailUrl);
        params.put("businessKey", safeText(business.getBusinessKey(), ""));
        params.put("processInstanceId", safeText(business.getProcessInstanceId(), ""));
        params.put("processName", processName);
        params.put("result", resultText);
        params.put("applyUserName", applyUserName);
        mergeVariables(params, variables);

        sendCollaborationCardMessage(business, connection, receiverIds, params,
                rejected ? "流程审批结果：已驳回" : "流程审批结果：已通过",
                buildDefaultResultCardDescription(processName, resultText, applyUserName),
                DEFAULT_RESULT_CARD_TEMPLATE, templateCodeOverride,
                FLOW_RESULT_MESSAGE_BIZ_TYPE, bizKey + ":COLLABORATION", detailUrl);
    }

    /**
     * 抄送渠道消息推送：按流程模型 notify_config 的 cc 事件渠道矩阵推送给抄送人
     */
    private void sendCcChannelMessages(FlowBusiness business, List<String> userIds, Map<String, Object> variables) {
        if (messageService == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        FlowNotifyConfig.ChannelConfig ccConfig = resolveChannelConfig(business, FlowNotifyConfig.EVENT_CC);
        if (ccConfig == null) {
            return;
        }
        Set<Long> receiverIds = toLongIds(userIds);
        if (receiverIds.isEmpty()) {
            return;
        }
        String title = "流程抄送通知";
        String content = "流程「" + safeText(business.getTitle(), business.getBusinessKey()) + "」已通过，请知悉。";
        String bizKey = safeText(business.getBusinessKey(), safeText(business.getProcessInstanceId(), "")) + ":CC";

        for (String channel : ccConfig.getChannels()) {
            String normalized = channel == null ? "" : channel.trim().toUpperCase();
            try {
                switch (normalized) {
                    case FlowNotifyConfig.CHANNEL_WEB:
                    case FlowNotifyConfig.CHANNEL_EMAIL:
                    case FlowNotifyConfig.CHANNEL_SMS:
                        MessageSendRequestDTO request = new MessageSendRequestDTO();
                        request.setTitle(title);
                        request.setContent(content);
                        request.setType("SYSTEM");
                        request.setChannel(normalized);
                        request.setSendScope("USERS");
                        request.setUserIds(receiverIds);
                        Map<String, Object> params = baseBusinessParams(business);
                        mergeVariables(params, variables);
                        request.setParams(params);
                        if (ccConfig.getTemplateCode() != null && !ccConfig.getTemplateCode().isBlank()) {
                            request.setTemplateCode(ccConfig.getTemplateCode().trim());
                        }
                        runWithBusinessTenant(business,
                                () -> messageService.sendIfAbsent(request, FLOW_CC_MESSAGE_BIZ_TYPE,
                                        bizKey + ":" + normalized));
                        log.info("流程抄送{}通知已推送: businessKey={}, receivers={}",
                                normalized, business.getBusinessKey(), receiverIds);
                        break;
                    case FlowNotifyConfig.CHANNEL_COLLABORATION:
                        sendCcCollaborationCard(business, receiverIds, ccConfig.getTemplateCode(), variables, bizKey);
                        break;
                    default:
                        log.warn("未知的抄送通知渠道，跳过: businessKey={}, channel={}",
                                business.getBusinessKey(), channel);
                }
            } catch (Exception e) {
                log.warn("流程抄送通知渠道推送失败，不影响其他渠道: businessKey={}, channel={}",
                        business.getBusinessKey(), channel, e);
            }
        }
    }

    /**
     * 抄送企业协同卡片
     */
    private void sendCcCollaborationCard(FlowBusiness business, Set<Long> receiverIds,
                                         String templateCodeOverride, Map<String, Object> variables, String bizKey) {
        if (socialConfigService == null) {
            return;
        }
        SysSocialConfig connection;
        try {
            connection = runWithBusinessTenantResult(business, () -> resolveTodoPushConnection(false));
        } catch (Exception e) {
            log.warn("解析抄送推送连接配置失败，跳过卡片推送: businessKey={}", business.getBusinessKey(), e);
            return;
        }
        if (connection == null) {
            return;
        }
        FlowModel model = loadFlowModel(business);
        String template = model != null && model.getTodoDetailUrlTemplate() != null
                && !model.getTodoDetailUrlTemplate().isBlank()
                ? model.getTodoDetailUrlTemplate().trim() : DEFAULT_RESULT_DETAIL_PATH;
        String detailUrl = appendH5BasePath(connection.getTodoPushH5Url(),
                renderUrlTemplate(template, null, business));
        if (!isHttpUrl(detailUrl)) {
            log.warn("抄送H5访问地址不是合法的http/https地址，跳过卡片推送: connectionId={}, h5Url={}",
                    connection.getId(), connection.getTodoPushH5Url());
            return;
        }

        String processName = cardText(safeText(business.getTitle(), business.getBusinessKey()), 60);
        Map<String, Object> params = new HashMap<>();
        params.put("msgType", "textcard");
        params.put("url", detailUrl);
        params.put("businessKey", safeText(business.getBusinessKey(), ""));
        params.put("processInstanceId", safeText(business.getProcessInstanceId(), ""));
        params.put("processName", processName);
        mergeVariables(params, variables);

        sendCollaborationCardMessage(business, connection, receiverIds, params,
                "流程抄送通知", buildDefaultCcCardDescription(processName),
                DEFAULT_CC_CARD_TEMPLATE, templateCodeOverride,
                FLOW_CC_MESSAGE_BIZ_TYPE, bizKey + ":COLLABORATION", detailUrl);
    }

    /** 结果/抄送消息基础模板变量 */
    private Map<String, Object> baseBusinessParams(FlowBusiness business) {
        Map<String, Object> params = new HashMap<>();
        params.put("businessKey", safeText(business.getBusinessKey(), ""));
        params.put("processInstanceId", safeText(business.getProcessInstanceId(), ""));
        params.put("processName", safeText(business.getTitle(), ""));
        params.put("jumpUrl", "/flow/business");
        return params;
    }

    /**
     * 加载流程模型并解析指定事件的渠道配置；未配置返回 null
     */
    private FlowNotifyConfig.ChannelConfig resolveChannelConfig(FlowBusiness business, String eventKey) {
        FlowModel model = loadFlowModel(business);
        FlowNotifyConfig notifyConfig = FlowNotifyConfig.parse(model == null ? null : model.getNotifyConfig());
        return notifyConfig == null ? null : notifyConfig.channelConfigOf(eventKey);
    }

    /** 用户ID集合转换（String → Long，忽略非法值） */
    private Set<Long> toLongIds(List<String> userIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (userIds == null) {
            return ids;
        }
        for (String userId : userIds) {
            Long id = parseUserId(userId);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 内置审批结果卡片排版（未配置启用模板时回退）
     */
    private String buildDefaultResultCardDescription(String processName, String resultText, String applyUserName) {
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

    /**
     * 内置抄送卡片排版（未配置启用模板时回退）
     */
    private String buildDefaultCcCardDescription(String processName) {
        StringBuilder description = new StringBuilder();
        description.append("<div class=\"gray\">流程抄送通知</div>");
        description.append("<div class=\"normal\">流程：").append(processName).append("</div>");
        description.append("<div class=\"highlight\">点击卡片查看详情 ›</div>");
        return description.toString();
    }

    private List<String> resolveCcRoleKeys(Object rawValue) {
        List<String> result = new ArrayList<>();
        if (rawValue instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rawValue) {
                addNonBlank(result, item);
            }
            return result;
        }
        if (rawValue instanceof String) {
            String text = ((String) rawValue).trim();
            if (text.isEmpty()) {
                return result;
            }
            for (String item : text.split("[,;，；]")) {
                addNonBlank(result, item);
            }
            return result;
        }
        addNonBlank(result, rawValue);
        return result;
    }

    private void addNonBlank(List<String> values, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            values.add(text);
        }
    }

    private List<String> resolveUserNames(List<String> userIds) {
        List<String> names = new ArrayList<>();
        for (String userId : userIds) {
            String name = null;
            try {
                Map<String, Object> userInfo = flowOrgIntegrationService.getUserInfo(userId);
                if (userInfo != null) {
                    Object rawName = userInfo.get("name");
                    if (rawName == null) {
                        rawName = userInfo.get("realName");
                    }
                    if (rawName != null) {
                        name = String.valueOf(rawName);
                    }
                }
            } catch (Exception e) {
                log.debug("解析抄送用户姓名失败: userId={}", userId);
            }
            names.add(name);
        }
        return names;
    }

    /**
     * 统一发布流程事件：根据 FlowModel.notifyType 互斥选择通知方式
     *
     * <ul>
     *   <li>{@code redis}   → 方案B: Redis Pub/Sub</li>
     *   <li>{@code webhook} → 方案C: HTTP Webhook（读取 FlowModel.webhookUrl）</li>
     *   <li>{@code none} 或未配置 → 不发送任何通知</li>
     * </ul>
     */
    private void publishEvent(FlowEventMessage message, String processDefKey) {
        if (processDefKey == null) {
            return;
        }
        Long tenantId = parseTenantId(message == null ? null : message.getTenantId());
        if (tenantId != null) {
            TenantContextHolder.executeWithTenant(tenantId, () -> doPublishEvent(message, processDefKey));
            return;
        }
        doPublishEvent(message, processDefKey);
    }

    private void doPublishEvent(FlowEventMessage message, String processDefKey) {
        try {
            FlowModel model = flowModelMapper.selectByModelKey(processDefKey);
            if (model == null) {
                log.debug("[FlowEvent] 未找到 FlowModel 配置，跳过通知: processDefKey={}", processDefKey);
                return;
            }

            String notifyType = model.getNotifyType();
            if (notifyType == null || "none".equalsIgnoreCase(notifyType)) {
                log.debug("[FlowEvent] notifyType=none，跳过通知: processDefKey={}", processDefKey);
                return;
            }

            // 方案B: Redis Pub/Sub
            if ("redis".equalsIgnoreCase(notifyType)) {
                if (flowEventPublisher != null) {
                    flowEventPublisher.publish(message);
                } else {
                    log.warn("[FlowEvent] notifyType=redis 但 FlowEventPublisher 未初始化（请确认已引入 spring-boot-starter-data-redis）");
                }
                return;
            }

            // 方案C: HTTP Webhook
            if ("webhook".equalsIgnoreCase(notifyType)) {
                if (model.getWebhookUrl() != null && !model.getWebhookUrl().isBlank()) {
                    flowWebhookNotifier.notify(model.getWebhookUrl(), message);
                } else {
                    log.warn("[FlowEvent] notifyType=webhook 但 webhookUrl 未配置: processDefKey={}", processDefKey);
                }
                return;
            }

            log.warn("[FlowEvent] 未知的 notifyType={}，跳过通知", notifyType);

        } catch (Exception e) {
            log.warn("[FlowEvent] 发布事件失败，不影响主流程: processDefKey={}, error={}", processDefKey, e.getMessage(), e);
        }
    }

    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            Long value = Long.parseLong(tenantId.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            log.warn("[FlowEvent] tenantId 格式错误，按当前上下文发布: tenantId={}", tenantId);
            return null;
        }
    }

    private void runWithBusinessTenant(FlowBusiness business, Runnable action) {
        if (action == null) {
            return;
        }
        Long tenantId = business == null ? null : business.getTenantId();
        if (tenantId != null && tenantId > 0) {
            TenantContextHolder.executeWithTenant(tenantId, action);
            return;
        }
        action.run();
    }

    private <T> T runWithBusinessTenantResult(FlowBusiness business, java.util.function.Supplier<T> supplier) {
        Long tenantId = business == null ? null : business.getTenantId();
        if (tenantId != null && tenantId > 0) {
            return TenantContextHolder.executeWithTenant(tenantId, supplier);
        }
        return supplier.get();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 卡片字段值：截断到指定字符数并转义企微 textcard description 中的 HTML 特殊字符 */
    private String cardText(String value, int maxChars) {
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
