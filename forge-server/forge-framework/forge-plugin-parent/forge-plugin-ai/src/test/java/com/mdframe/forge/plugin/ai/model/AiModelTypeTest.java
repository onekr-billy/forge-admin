package com.mdframe.forge.plugin.ai.model;

import com.mdframe.forge.plugin.ai.model.constant.AiModelType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiModelTypeTest {

    @Test
    void allCodesAreUniqueAndMatchModelTypes() {
        assertEquals("chat", AiModelType.CHAT.getCode());
        assertEquals("vision", AiModelType.VISION.getCode());
        assertEquals("video_understanding", AiModelType.VIDEO_UNDERSTANDING.getCode());
        assertEquals("audio_understanding", AiModelType.AUDIO_UNDERSTANDING.getCode());
        assertEquals("embedding", AiModelType.EMBEDDING.getCode());
        assertEquals("rerank", AiModelType.RERANK.getCode());
        assertEquals("image_generation", AiModelType.IMAGE_GENERATION.getCode());
        assertEquals("video_generation", AiModelType.VIDEO_GENERATION.getCode());
        assertEquals("asr", AiModelType.ASR.getCode());
        assertEquals("tts", AiModelType.TTS.getCode());
    }

    @Test
    void fromCodeResolvesKnownAndUnknown() {
        assertEquals(AiModelType.CHAT, AiModelType.fromCode("chat"));
        assertEquals(AiModelType.VISION, AiModelType.fromCode("vision"));
        assertEquals(AiModelType.VIDEO_UNDERSTANDING, AiModelType.fromCode("video_understanding"));
        assertEquals(AiModelType.AUDIO_UNDERSTANDING, AiModelType.fromCode("audio_understanding"));
        assertEquals(AiModelType.EMBEDDING, AiModelType.fromCode("embedding"));
        assertEquals(AiModelType.RERANK, AiModelType.fromCode("rerank"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.fromCode("image_generation"));
        assertEquals(AiModelType.VIDEO_GENERATION, AiModelType.fromCode("video_generation"));
        assertEquals(AiModelType.ASR, AiModelType.fromCode("asr"));
        assertEquals(AiModelType.TTS, AiModelType.fromCode("tts"));
    }

    @Test
    void fromCodeMapsLegacyValues() {
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.fromCode("image"));
        assertEquals(AiModelType.ASR, AiModelType.fromCode("audio"));
    }

    @Test
    void fromCodeReturnsNullForUnknownAndNull() {
        assertNull(AiModelType.fromCode("unknown_type"));
        assertNull(AiModelType.fromCode(null));
    }

    @Test
    void inferFromModelId_embedding() {
        assertEquals(AiModelType.EMBEDDING, AiModelType.inferFromModelId("text-embedding-3-small"));
        assertEquals(AiModelType.EMBEDDING, AiModelType.inferFromModelId("text-embedding-ada-002"));
        assertEquals(AiModelType.EMBEDDING, AiModelType.inferFromModelId("bge-large-zh-v1.5-embed"));
    }

    @Test
    void inferFromModelId_rerank() {
        assertEquals(AiModelType.RERANK, AiModelType.inferFromModelId("bge-reranker-v2-m3"));
        assertEquals(AiModelType.RERANK, AiModelType.inferFromModelId("cross-encoder-ms-marco-MiniLM-L-6-v2"));
    }

    @Test
    void inferFromModelId_imageGeneration() {
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.inferFromModelId("dall-e-3"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.inferFromModelId("flux-1-schnell"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.inferFromModelId("stable-diffusion-xl"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.inferFromModelId("wanx-v1"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.inferFromModelId("wan2.1-t2i-turbo"));
    }

    @Test
    void inferFromModelId_asr() {
        assertEquals(AiModelType.ASR, AiModelType.inferFromModelId("whisper-1"));
        assertEquals(AiModelType.ASR, AiModelType.inferFromModelId("paraformer-v2"));
        assertEquals(AiModelType.ASR, AiModelType.inferFromModelId("sensevoice-small"));
    }

    @Test
    void inferFromModelId_tts() {
        assertEquals(AiModelType.TTS, AiModelType.inferFromModelId("tts-1"));
        assertEquals(AiModelType.TTS, AiModelType.inferFromModelId("cosyvoice-v1"));
    }

    @Test
    void inferFromModelId_vision() {
        assertEquals(AiModelType.VISION, AiModelType.inferFromModelId("qwen-vl-plus"));
        assertEquals(AiModelType.VISION, AiModelType.inferFromModelId("qwen2.5-vl-72b-instruct"));
        assertEquals(AiModelType.VISION, AiModelType.inferFromModelId("qwen3-vl-235b"));
    }

    @Test
    void inferFromModelId_videoUnderstanding() {
        assertEquals(AiModelType.VIDEO_UNDERSTANDING, AiModelType.inferFromModelId("qwen-vl-plus-video"));
        assertEquals(AiModelType.VIDEO_UNDERSTANDING, AiModelType.inferFromModelId("qwen2.5-vl-72b-instruct-video"));
    }

    @Test
    void inferFromModelId_audioUnderstanding() {
        assertEquals(AiModelType.AUDIO_UNDERSTANDING, AiModelType.inferFromModelId("qwen-audio-turbo"));
        assertEquals(AiModelType.AUDIO_UNDERSTANDING, AiModelType.inferFromModelId("qwen2.5-audio-7b"));
    }

    @Test
    void inferFromModelId_videoGeneration() {
        assertEquals(AiModelType.VIDEO_GENERATION, AiModelType.inferFromModelId("wan2.1-t2v-turbo"));
        assertEquals(AiModelType.VIDEO_GENERATION, AiModelType.inferFromModelId("wan2.1-i2v-turbo"));
        assertEquals(AiModelType.VIDEO_GENERATION, AiModelType.inferFromModelId("maku-video-gen"));
    }

    @Test
    void inferFromModelId_chatFallback() {
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("gpt-4o"));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("deepseek-chat"));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("qwen-plus"));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("glm-4"));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("minimax-text-01"));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("qwen-omni-turbo"));
    }

    @Test
    void inferFromModelId_nullAndBlank() {
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId(null));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId(""));
        assertEquals(AiModelType.CHAT, AiModelType.inferFromModelId("   "));
    }
}
