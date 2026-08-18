package com.mdframe.forge.app.server.bridge;

import com.mdframe.forge.plugin.generator.service.AiClientAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * App 服务只提供低代码运行态，不承载 Admin 侧的 AI 生成能力。
 *
 * <p>generator 中的部分服务依赖 AI 适配器，即使运行态请求不会触发 AI，也需要一个
 * 明确的失败关闭实现完成容器装配，避免 App 服务错误地引入 AI 管理插件。</p>
 */
@Slf4j
@Component
public class AppAiClientAdapter implements AiClientAdapter {

    @Override
    public AiClientResult call(String agentCode, String message, Map<String, String> contextVars) {
        return fallback(agentCode);
    }

    @Override
    public AiClientResult call(String agentCode, String message, Map<String, String> contextVars,
                               Integer timeoutSeconds) {
        return fallback(agentCode);
    }

    @Override
    public Flux<String> stream(String userInput, String agentCode, String message,
                               Map<String, String> contextVars) {
        return Flux.error(unsupported("stream"));
    }

    @Override
    public Flux<String> stream(String userInput, String sessionId, String agentCode, String message,
                               Map<String, String> contextVars) {
        return Flux.error(unsupported("stream"));
    }

    @Override
    public Flux<String> stream(String userInput, String sessionId, String agentCode, String message,
                               Map<String, String> contextVars, Long providerId, Long modelId,
                               Double temperature, Integer maxTokens) {
        return Flux.error(unsupported("stream"));
    }

    private AiClientResult fallback(String agentCode) {
        String reason = "App 服务未启用 AI 生成能力: " + agentCode;
        log.debug("[AppAiClientAdapter] {}", reason);
        return AiClientResult.fallback(reason);
    }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("App 服务未启用 AI " + operation + " 能力");
    }
}
