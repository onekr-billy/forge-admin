package com.mdframe.forge.starter.idempotent.strategy;

import com.mdframe.forge.starter.idempotent.annotation.Idempotent;
import com.mdframe.forge.starter.idempotent.exception.TokenInvalidException;
import com.mdframe.forge.starter.idempotent.properties.TokenProperties;
import com.mdframe.forge.starter.idempotent.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenRequiredStrategyHandlerTest {

    private TokenService tokenService;
    private IdempotentStrategyHandler delegate;
    private TokenRequiredStrategyHandler handler;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        delegate = mock(IdempotentStrategyHandler.class);
        TokenProperties properties = new TokenProperties();
        properties.setHeader("X-Idempotent-Token");
        handler = new TokenRequiredStrategyHandler(tokenService, properties, delegate);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void proceedsOnlyAfterAtomicConsumption() throws Throwable {
        bindRequest("token-value");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Idempotent annotation = mock(Idempotent.class);
        when(annotation.prefix()).thenReturn("submit");
        when(tokenService.consumeToken("token-value", "submit")).thenReturn(true);
        when(delegate.handle(joinPoint, annotation, "key")).thenReturn("ok");

        Object result = handler.handle(joinPoint, annotation, "key");

        assertEquals("ok", result);
        verify(tokenService, never()).validateToken("token-value", "submit");
    }

    @Test
    void rejectsWhenAtomicConsumptionLosesTheRace() throws Throwable {
        bindRequest("token-value");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Idempotent annotation = mock(Idempotent.class);
        when(annotation.prefix()).thenReturn("submit");
        when(tokenService.consumeToken("token-value", "submit")).thenReturn(false);

        assertThrows(TokenInvalidException.class,
                () -> handler.handle(joinPoint, annotation, "key"));
        verify(delegate, never()).handle(joinPoint, annotation, "key");
    }

    private void bindRequest(String token) {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader("X-Idempotent-Token", token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
