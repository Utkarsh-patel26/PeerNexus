package com.example.jtorrent.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final String masterPassword;
    private final SecureRandom random = new SecureRandom();
    private final long tokenValidityMs;

    public AuthManager(String masterPassword, long tokenValidityMs) {
        this.masterPassword = masterPassword;
        this.tokenValidityMs = tokenValidityMs;
    }

    public AuthManager(String masterPassword) {
        this(masterPassword, 24 * 60 * 60 * 1000L);
    }

    public boolean authenticate(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return masterPassword == null || masterPassword.isEmpty();
        }

        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return validateToken(token);
        }

        if (authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)),
                    StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);
            if (parts.length == 2) {
                return validatePassword(parts[1]);
            }
        }

        return false;
    }

    public String generateToken(String username) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokens.put(token, new TokenInfo(username, System.currentTimeMillis() + tokenValidityMs));
        return token;
    }

    public void revokeToken(String token) {
        tokens.remove(token);
    }

    public void revokeAllTokens() {
        tokens.clear();
    }

    private boolean validateToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() > info.expiresAt) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    private boolean validatePassword(String password) {
        if (masterPassword == null || masterPassword.isEmpty()) {
            return true;
        }
        return constantTimeEquals(masterPassword, password);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record TokenInfo(String username, long expiresAt) {
    }
}
