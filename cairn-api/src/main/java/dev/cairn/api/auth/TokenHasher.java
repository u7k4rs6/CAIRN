package dev.cairn.api.auth;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 for personal access tokens: a fast hash is the right choice here, unlike
 * for passwords (security doc, section 2.3 vs 2.2). Tokens are high-entropy random
 * values the server itself generates, not something a human chose, so there is no
 * low-entropy guessing attack for a slow hash to defend against; a fast hash keeps
 * every Git-over-HTTP request's auth check cheap.
 */
@Component
public class TokenHasher {

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
