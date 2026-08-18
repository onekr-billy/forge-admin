package com.mdframe.forge.plugin.ai.model.constant;

/**
 * AI 模型类型（细分）。
 * <p>
 * 分类对齐阿里百炼模型广场：对话 / 视觉理解 / 视频理解 / 音频理解 /
 * 语音识别 / 语音合成 / 文生图 / 视频生成 / 向量化 / 重排。
 * </p>
 * <p>
 * 视觉/视频/音频理解均为多模态对话模型，走 OpenAI 兼容 Chat 协议（可传图/视频/音频）。
 * </p>
 */
public enum AiModelType {

    /** 对话/文本生成（大语言模型） */
    CHAT("chat"),
    /** 视觉理解（图像/视频多模态，qwen-vl 系列） */
    VISION("vision"),
    /** 视频理解（qwen-vl-video 系列） */
    VIDEO_UNDERSTANDING("video_understanding"),
    /** 音频理解（qwen-audio 系列） */
    AUDIO_UNDERSTANDING("audio_understanding"),
    /** 向量化（text-embedding / qwen3-embedding） */
    EMBEDDING("embedding"),
    /** 重排（gte-rerank / text-rerank） */
    RERANK("rerank"),
    /** 文生图/图生图（wanx / flux / stable-diffusion） */
    IMAGE_GENERATION("image_generation"),
    /** 视频生成（wan t2v/i2v / maku） */
    VIDEO_GENERATION("video_generation"),
    /** 语音识别（paraformer / sensevoice / whisper） */
    ASR("asr"),
    /** 语音合成（sambert / cosyvoice） */
    TTS("tts");

    private final String code;

    AiModelType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 由 code 解析枚举。
     * 兼容存量宽泛值：image → IMAGE_GENERATION，audio → ASR（历史 model_type 只有 image/audio 四类）。
     * 未知返回 null（兼容未知类型）。
     */
    public static AiModelType fromCode(String code) {
        if (code == null) {
            return null;
        }
        if ("image".equals(code)) {
            return IMAGE_GENERATION;
        }
        if ("audio".equals(code)) {
            return ASR;
        }
        for (AiModelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据模型标识（modelId）启发式推断模型类型。
     * <p>
     * 匹配规则（按优先级，与前端 {@code inferModelType} 保持一致）：
     * <ol>
     *   <li>embedding / embed → EMBEDDING</li>
     *   <li>rerank / re-rank / cross-encoder → RERANK</li>
     *   <li>t2v / i2v / video-gen / maku → VIDEO_GENERATION</li>
     *   <li>t2i / i2i / dall-e / imagen / flux / midjourney / stable-diffusion / sdxl / cogview / wanx → IMAGE_GENERATION</li>
     *   <li>video（非生成）→ VIDEO_UNDERSTANDING</li>
     *   <li>whisper / asr / speech-to-text / paraformer / sensevoice → ASR</li>
     *   <li>tts / speech-to-speech / speech-synthesis / cosyvoice / sambert → TTS</li>
     *   <li>audio → AUDIO_UNDERSTANDING</li>
     *   <li>vl / vision → VISION</li>
     *   <li>其余 → CHAT（默认兜底）</li>
     * </ol>
     *
     * @param modelId 模型标识（如 gpt-4o、text-embedding-3-small、qwen-vl-plus）
     * @return 推断的模型类型，null 输入返回 CHAT
     */
    public static AiModelType inferFromModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return CHAT;
        }
        String lower = modelId.toLowerCase();
        // embedding
        if (lower.contains("embedding") || lower.contains("embed")) {
            return EMBEDDING;
        }
        // rerank
        if (lower.contains("rerank") || lower.contains("re-rank") || lower.contains("cross-encoder")) {
            return RERANK;
        }
        // 视频生成：文生视频/图生视频（t2v/i2v），以及 maku 等
        if (lower.contains("t2v") || lower.contains("i2v")
                || lower.contains("video-gen") || lower.contains("videogen")
                || lower.contains("maku")) {
            return VIDEO_GENERATION;
        }
        // 图像生成：文生图/图生图
        if (lower.contains("t2i") || lower.contains("i2i")
                || lower.contains("dall-e") || lower.contains("dalle")
                || lower.contains("imagen") || lower.contains("flux")
                || lower.contains("midjourney") || lower.contains("stable-diffusion")
                || lower.contains("sdxl") || lower.contains("cogview")
                || lower.contains("wanx")) {
            return IMAGE_GENERATION;
        }
        // 视频理解（多模态对话，ID 含 video 且非生成类）
        if (lower.contains("video")) {
            return VIDEO_UNDERSTANDING;
        }
        // asr
        if (lower.contains("whisper") || lower.contains("asr")
                || lower.contains("speech-to-text") || lower.contains("paraformer")
                || lower.contains("sensevoice")) {
            return ASR;
        }
        // tts
        if (lower.contains("tts") || lower.contains("speech-to-speech")
                || lower.contains("speech-synthesis") || lower.contains("cosyvoice")
                || lower.contains("sambert")) {
            return TTS;
        }
        // 音频理解（qwen-audio 系列）
        if (lower.contains("audio")) {
            return AUDIO_UNDERSTANDING;
        }
        // 视觉理解（qwen-vl 系列）
        if (lower.contains("vl") || lower.contains("vision")) {
            return VISION;
        }
        // 默认为对话模型
        return CHAT;
    }
}
