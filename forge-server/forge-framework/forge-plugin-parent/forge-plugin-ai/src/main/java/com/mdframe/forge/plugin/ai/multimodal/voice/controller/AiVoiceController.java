package com.mdframe.forge.plugin.ai.multimodal.voice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.ai.multimodal.voice.AiAsrService;
import com.mdframe.forge.plugin.ai.multimodal.voice.AiTtsService;
import com.mdframe.forge.plugin.ai.multimodal.voice.dto.AiVoiceSynthesizeDTO;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI语音控制器（ASR/TTS）
 */
@RestController
@RequestMapping("/ai/voice")
@RequiredArgsConstructor
public class AiVoiceController {

    private final AiAsrService asrService;
    private final AiTtsService ttsService;

    /**
     * 语音识别（ASR）
     */
    @SaCheckPermission("ai:voice:asr")
    @PostMapping("/asr")
    public RespInfo<String> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("agentId") Long agentId) {
        String text = asrService.transcribe(audio, agentId);
        return RespInfo.success(text);
    }

    /**
     * 语音合成（TTS）
     */
    @SaCheckPermission("ai:voice:tts")
    @PostMapping("/tts")
    public RespInfo<Long> synthesize(@RequestBody AiVoiceSynthesizeDTO params) {
        Long fileId = ttsService.synthesize(params.getText(), params.getAgentId());
        return RespInfo.success(fileId);
    }
}
