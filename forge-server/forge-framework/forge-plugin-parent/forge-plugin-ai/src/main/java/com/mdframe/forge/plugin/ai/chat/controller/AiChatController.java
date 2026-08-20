package com.mdframe.forge.plugin.ai.chat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.chat.dto.AIGenerateRequest;
import com.mdframe.forge.plugin.ai.chat.dto.ChatRequest;
import com.mdframe.forge.plugin.ai.chat.service.AiChatMessageAssembler;
import com.mdframe.forge.plugin.ai.chat.service.AiChatService;
import com.mdframe.forge.plugin.ai.chat.vo.AiChatMessagePageVO;
import com.mdframe.forge.plugin.ai.chat.vo.AiChatMessageVO;
import com.mdframe.forge.plugin.ai.session.domain.AiChatSession;
import com.mdframe.forge.plugin.ai.session.dto.AiSessionPageQuery;
import com.mdframe.forge.plugin.ai.session.dto.AiSessionSaveDTO;
import com.mdframe.forge.plugin.ai.session.service.AiChatSessionService;
import com.mdframe.forge.plugin.ai.session.vo.AiSessionVO;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话 Controller
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService chatService;
    private final AiChatSessionService sessionService;
    private final AiChatMessageAssembler messageAssembler;

    /**
     * AI 生成大屏（非流式）
     */
    @PostMapping("/generate")
    public RespInfo<String> generate(@RequestBody AIGenerateRequest request) {
        try {
            String result = chatService.generateDashboard(request);
            return RespInfo.success(result);
        } catch (Exception e) {
            log.error("AI 生成大屏失败", e);
            return RespInfo.error("AI 生成失败: " + e.getMessage());
        }
    }

    /**
     * AI 生成大屏（SSE 流式输出）
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateStream(@RequestBody AIGenerateRequest request) {
        return chatService.generateDashboardStream(request)
                .map(chunk -> ServerSentEvent.builder(chunk)
                        .event("message")
                        .build())
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]")
                        .event("done")
                        .build()))
                .onErrorResume(e -> {
                    log.error("AI 流式生成大屏失败", e);
                    return Flux.just(ServerSentEvent.builder("错误: " + e.getMessage())
                            .event("error")
                            .build());
                });
    }

    /**
     * AI 对话（SSE 流式输出，支持多轮上下文）
     * 请求中需传入 sessionId（同一会话始终发相同值，新对话可不传或传新UUID）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return chatService.chatStream(request.getContent(), request.getAgentCode(),
                        request.getSessionId(), userId, request.getProviderId(),
                        request.getModelName(), request.getTemperature(), request.getMaxTokens(),
                        request.getProjectName(), request.getCanvasContext())
                .map(chunk -> ServerSentEvent.builder(chunk)
                        .event("message")
                        .build())
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]")
                        .event("done")
                        .build()))
                .onErrorResume(e -> {
                    log.error("AI 对话流式输出失败", e);
                    return Flux.just(ServerSentEvent.builder("错误: " + e.getMessage())
                            .event("error")
                            .build());
                });
    }

    // ==================== 会话管理接口 ====================

    /**
     * 获取当前用户的历史会话列表
     */
    @GetMapping("/session/list")
    public RespInfo<List<AiChatSession>> sessionList() {
        Long userId = StpUtil.getLoginIdAsLong();
        return RespInfo.success(sessionService.listByUser(userId));
    }

    /**
     * 会话分页查询（决策 32）：当前用户 + 关键词 + Agent 过滤 + 上滑加载。
     * 用户侧强制按当前登录用户过滤，避免看到他人会话。
     */
    @GetMapping("/session/page")
    public RespInfo<Page<AiSessionVO>> sessionPage(AiSessionPageQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        return RespInfo.success(sessionService.userPage(query, userId));
    }

    /**
     * 显式创建会话（决策 29/30）：新会话即落库并绑定 Agent，不再只靠前端本地 unshift。
     * 幂等——sessionId 已存在则返回原会话；标题由服务端规则生成（决策 34）。
     */
    @PostMapping("/session")
    public RespInfo<AiChatSession> createSession(@RequestBody AiSessionSaveDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        AiChatSession session = sessionService.createSession(
                dto.getSessionId(), userId, tenantId, dto.getAgentCode(), dto.getSessionName());
        return RespInfo.success(session);
    }

    /**
     * 重命名会话（决策 31）：标题以服务端为准，仅本人可改。
     */
    @PutMapping("/session/{sessionId}")
    public RespInfo<Void> renameSession(@PathVariable String sessionId, @RequestBody AiSessionSaveDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean ok = sessionService.renameSession(sessionId, userId, dto.getSessionName());
        return ok ? RespInfo.success() : RespInfo.error("重命名失败：会话不存在或无权限");
    }

    /**
     * 获取指定会话的对话明细
     */
    @GetMapping("/session/{sessionId}/messages")
    public RespInfo<List<AiChatMessageVO>> sessionMessages(@PathVariable String sessionId) {
        return RespInfo.success(messageAssembler.assembleSessionMessages(sessionId));
    }

    /**
     * 分页获取会话消息（变更B#7：长会话上滑加载更早消息）。
     * beforeId 为空取最近一页；上滑时传当前最早消息 id 取更早一页。
     */
    @GetMapping("/session/{sessionId}/messages/page")
    public RespInfo<AiChatMessagePageVO> sessionMessagesPage(
            @PathVariable String sessionId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") Integer size) {
        return RespInfo.success(messageAssembler.assembleSessionMessagesPage(sessionId, beforeId, size));
    }

    /**
     * 删除单条消息（软删除）
     */
    @DeleteMapping("/message/{recordId}")
    public RespInfo<Void> deleteMessage(@PathVariable Long recordId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean ok = sessionService.deleteMessage(recordId, userId);
        return ok ? RespInfo.success() : RespInfo.error("删除失败：消息不存在或无权限");
    }

    /**
     * 置顶/取消置顶会话
     */
    @PutMapping("/session/{sessionId}/pin")
    public RespInfo<Void> pinSession(@PathVariable String sessionId, @RequestParam boolean pinned) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean ok = sessionService.pinSession(sessionId, userId, pinned);
        return ok ? RespInfo.success() : RespInfo.error("置顶失败：会话不存在或无权限");
    }

    /**
     * 删除会话（软删除， status=1）
     */
    @DeleteMapping("/session/{sessionId}")
    public RespInfo<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return RespInfo.success();
    }
}
