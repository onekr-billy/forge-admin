package com.mdframe.forge.plugin.ai.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.chat.service.AiChatMessageAssembler;
import com.mdframe.forge.plugin.ai.chat.vo.AiChatMessageVO;
import com.mdframe.forge.plugin.ai.session.dto.AiSessionPageQuery;
import com.mdframe.forge.plugin.ai.session.service.AiChatSessionService;
import com.mdframe.forge.plugin.ai.session.vo.AiExperienceMetricsVO;
import com.mdframe.forge.plugin.ai.session.vo.AiSessionStatisticsVO;
import com.mdframe.forge.plugin.ai.session.vo.AiSessionVO;
import com.mdframe.forge.plugin.ai.session.domain.AiChatSession;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai/admin/session")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class AiSessionAdminController {

    private final AiChatSessionService sessionService;
    private final AiChatMessageAssembler messageAssembler;

    @GetMapping("/page")
    public RespInfo<Page<AiSessionVO>> page(AiSessionPageQuery query) {
        return RespInfo.success(sessionService.adminPage(query));
    }

    @GetMapping("/{sessionId}/messages")
    public RespInfo<List<AiChatMessageVO>> messages(@PathVariable String sessionId) {
        return RespInfo.success(messageAssembler.assembleSessionMessages(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public RespInfo<Void> delete(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return RespInfo.success();
    }

    @GetMapping("/statistics")
    public RespInfo<AiSessionStatisticsVO> statistics() {
        return RespInfo.success(sessionService.getStatistics());
    }

    @GetMapping("/experience-metrics")
    public RespInfo<AiExperienceMetricsVO> experienceMetrics() {
        return RespInfo.success(sessionService.getExperienceMetrics());
    }

    @PutMapping("/{sessionId}/metadata")
    public RespInfo<Void> updateSessionMetadata(@PathVariable String sessionId, @RequestBody Map<String, Object> metadata) {
        AiChatSession session = sessionService.getById(sessionId);
        if (session == null) {
            return RespInfo.error("会话不存在");
        }
        session.setMetadata(metadata);
        sessionService.updateById(session);
        return RespInfo.success();
    }
}
