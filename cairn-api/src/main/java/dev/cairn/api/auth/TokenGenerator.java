package dev.cairn.api.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates the high-entropy plaintext personal access tokens {@link TokenHasher}
 * then hashes for storage (security doc, section 2.3): 32 bytes from a
 * {@link SecureRandom}, url-safe base64 encoded, prefixed so a token is
 * recognizable at a glance (the same convention GitHub/GitLab tokens use).
 */
@Component
public class TokenGenerator {

    private static final String PREFIX = "cairn_";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
