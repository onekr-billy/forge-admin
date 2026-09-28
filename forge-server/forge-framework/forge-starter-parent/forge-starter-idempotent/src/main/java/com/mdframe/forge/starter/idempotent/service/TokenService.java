package com.mdframe.forge.starter.idempotent.service;

public interface TokenService {
    
    String generateToken(String prefix);
    
    boolean validateToken(String token, String prefix);
    
    boolean consumeToken(String token, String prefix);
    
    boolean isTokenConsumed(String token, String prefix);
}
