package com.example.jtorrent.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthManager Tests")
class AuthManagerTest {

    @Test
    @DisplayName("Should allow unauthenticated access when master password is empty")
    void shouldAllowUnauthenticatedAccessWhenMasterPasswordEmpty() {
        AuthManager authManager = new AuthManager("");

        assertTrue(authManager.authenticate(null));
        assertTrue(authManager.authenticate(""));
    }

    @Test
    @DisplayName("Should deny unauthenticated access when master password is configured")
    void shouldDenyUnauthenticatedAccessWhenMasterPasswordConfigured() {
        AuthManager authManager = new AuthManager("secret");

        assertFalse(authManager.authenticate(null));
        assertFalse(authManager.authenticate(""));
    }

    @Test
    @DisplayName("Should authenticate with valid basic auth")
    void shouldAuthenticateWithValidBasicAuth() {
        AuthManager authManager = new AuthManager("secret");
        String credentials = Base64.getEncoder().encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8));

        assertTrue(authManager.authenticate("Basic " + credentials));
    }

    @Test
    @DisplayName("Should reject basic auth with invalid password")
    void shouldRejectBasicAuthWithInvalidPassword() {
        AuthManager authManager = new AuthManager("secret");
        String credentials = Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8));

        assertFalse(authManager.authenticate("Basic " + credentials));
    }

    @Test
    @DisplayName("Should reject unsupported auth scheme")
    void shouldRejectUnsupportedAuthScheme() {
        AuthManager authManager = new AuthManager("secret");

        assertFalse(authManager.authenticate("Digest abc"));
    }

    @Test
    @DisplayName("Should throw for malformed basic auth base64")
    void shouldThrowForMalformedBasicAuthBase64() {
        AuthManager authManager = new AuthManager("secret");

        assertThrows(IllegalArgumentException.class, () -> authManager.authenticate("Basic ###"));
    }

    @Test
    @DisplayName("Should generate bearer token and validate it")
    void shouldGenerateBearerTokenAndValidateIt() {
        AuthManager authManager = new AuthManager("secret");

        String token = authManager.generateToken("alice");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(authManager.authenticate("Bearer " + token));
    }

    @Test
    @DisplayName("Should reject revoked token")
    void shouldRejectRevokedToken() {
        AuthManager authManager = new AuthManager("secret");
        String token = authManager.generateToken("bob");

        authManager.revokeToken(token);

        assertFalse(authManager.authenticate("Bearer " + token));
    }

    @Test
    @DisplayName("Should reject all tokens after revoke all")
    void shouldRejectAllTokensAfterRevokeAll() {
        AuthManager authManager = new AuthManager("secret");
        String token1 = authManager.generateToken("bob");
        String token2 = authManager.generateToken("alice");

        authManager.revokeAllTokens();

        assertFalse(authManager.authenticate("Bearer " + token1));
        assertFalse(authManager.authenticate("Bearer " + token2));
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        AuthManager authManager = new AuthManager("secret", -1);
        String token = authManager.generateToken("alice");

        assertFalse(authManager.authenticate("Bearer " + token));
    }

    @Test
    @DisplayName("Should produce deterministic hash for same password")
    void shouldProduceDeterministicHashForSamePassword() {
        AuthManager authManager = new AuthManager("secret");

        String hash1 = authManager.hashPassword("abc123");
        String hash2 = authManager.hashPassword("abc123");
        String hash3 = authManager.hashPassword("abc124");

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertNotEquals("abc123", hash1);
    }
}
