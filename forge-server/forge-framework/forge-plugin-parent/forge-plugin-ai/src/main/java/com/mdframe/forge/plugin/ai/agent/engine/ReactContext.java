package com.mdframe.forge.plugin.ai.agent.engine;

import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环上下文
 */
@Data
public class ReactContext {

    private String agentCode;
    private Long agentId;
    private Long tenantId;
    /** 发起用户ID（请求线程注入，供埋点归属；loop 线程无 Sa-Token 上下文） */
    private Long userId;
    private String sessionId;
    private int turnIndex;
    private int maxIters;
    private String toolGroupMode;

    private ChatModel chatModel;
    private AiAgent agent;

    private String systemPrompt;
    private String userMessage;

    /**
     * 图片附件（fileId列表），用于多模态消息
     */
    private List<Long> imageFileIds;

    /**
     * 文件访问URL构建函数（由外部注入，用于将fileId转为可访问URL）
     */
    private java.util.function.Function<Long, String> fileUrlResolver;

    /**
     * 对话历史
     */
    private List<Message> history = new ArrayList<>();

    /**
     * 取消标识
     */
    private volatile boolean cancelled;

    /**
     * 当前轮次的工具调用和结果（用于构造下一轮消息）
     */
    private List<String> toolCallResults = new ArrayList<>();

    /**
     * 本 Agent 运行时应向模型声明的工具列表（已解析为 AgentTool，去重）。
     * 由 AgentEngineService.buildContext 依据「工具绑定表 + 知识库绑定」解析注入；
     * 空列表表示本次不声明任何工具（普通对话 Agent，行为与历史一致）。
     * ReactLoop.callModel 据此构造 OpenAiChatOptions.toolCallbacks。
     */
    private List<AgentTool> boundTools = new ArrayList<>();

    public void addToolResult(String toolName, String toolArgs, String result) {
        toolCallResults.add("{\"tool\":\"" + toolName + "\",\"args\":" + toolArgs + ",\"result\":\"" + result + "\"}");
    }

    /**
     * 构建发送给 LLM 的消息列表
     */
    public List<Message> buildMessages() {
        List<Message> messages = new ArrayList<>();

        // 系统提示词
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // 历史消息
        messages.addAll(history);

        // 用户消息（支持多模态）
        if (userMessage != null && !userMessage.isBlank()) {
            UserMessage userMsg = buildUserMessage(userMessage);
            messages.add(userMsg);
        }

        // 如果有工具结果，构造工具结果消息
        if (!toolCallResults.isEmpty()) {
            StringBuilder sb = new StringBuilder("工具执行结果：\n");
            for (String result : toolCallResults) {
                sb.append(result).append("\n");
            }
            messages.add(new AssistantMessage(sb.toString()));
        }

        return messages;
    }

    /**
     * 构建用户消息，如果存在图片附件则构造多模态消息
     */
    private UserMessage buildUserMessage(String text) {
        if (imageFileIds == null || imageFileIds.isEmpty() || fileUrlResolver == null) {
            return new UserMessage(text);
        }

        try {
            List<Media> mediaList = new ArrayList<>();
            for (Long fileId : imageFileIds) {
                String url = fileUrlResolver.apply(fileId);
                if (url != null && !url.isBlank()) {
                    mediaList.add(new Media(MimeTypeUtils.IMAGE_PNG, new URI(url)));
                }
            }
            if (!mediaList.isEmpty()) {
                return UserMessage.builder()
                        .text(text)
                        .media(mediaList)
                        .build();
            }
        } catch (Exception e) {
            // 多模态构造失败，降级为纯文本
        }
        return new UserMessage(text);
    }
}
