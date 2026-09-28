package com.mdframe.forge.starter.idempotent.strategy;

import com.mdframe.forge.starter.idempotent.annotation.Idempotent;
import com.mdframe.forge.starter.idempotent.exception.TokenInvalidException;
import com.mdframe.forge.starter.idempotent.properties.TokenProperties;
import com.mdframe.forge.starter.idempotent.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.dao.DataAccessException;
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
        
        boolean consumed;
        try {
            consumed = tokenService.consumeToken(token, prefix);
        } catch (DataAccessException exception) {
            log.warn("Token模式: Redis原子消费结果不确定，prefix={}, errorType={}",
                    prefix, exception.getClass().getSimpleName());
            throw new TokenInvalidException("幂等校验服务暂不可用，请重新获取Token后重试");
        }
        if (!consumed) {
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
            log.warn("提取Token失败，errorType={}", e.getClass().getSimpleName());
        }
        return null;
    }
}
