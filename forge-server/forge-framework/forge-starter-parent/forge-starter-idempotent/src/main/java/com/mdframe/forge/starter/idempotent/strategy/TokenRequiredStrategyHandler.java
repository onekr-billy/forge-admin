package com.mdframe.forge.starter.idempotent.strategy;

import com.mdframe.forge.starter.idempotent.annotation.Idempotent;
import com.mdframe.forge.starter.idempotent.exception.TokenInvalidException;
import com.mdframe.forge.starter.idempotent.properties.TokenProperties;
import com.mdframe.forge.starter.idempotent.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@RequiredArgsConstructor
public class TokenRequiredStrategyHandler implements IdempotentStrategyHandler {
    
    private final TokenService tokenService;
    private final TokenProperties tokenProperties;
    private final IdempotentStrategyHandler delegateHandler;
    
    @Override
    public Object handle(ProceedingJoinPoint joinPoint, Idempotent annotation, String idempotentKey) throws Throwable {
        String token = extractToken();
        String prefix = annotation.prefix();
        
        if (!tokenService.consumeToken(token, prefix)) {
            log.warn("Token模式: Token原子消费失败, prefix={}", prefix);
            throw new TokenInvalidException("Token无效或已过期");
        }
        log.debug("Token模式: Token验证并消费成功, prefix={}", prefix);
        
        return delegateHandler.handle(joinPoint, annotation, idempotentKey);
    }
    
    private String extractToken() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader(tokenProperties.getHeader());
            }
        } catch (Exception e) {
            log.warn("提取Token失败: {}", e.getMessage());
        }
        return null;
    }
}
