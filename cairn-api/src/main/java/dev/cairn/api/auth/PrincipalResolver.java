package dev.cairn.api.auth;

import dev.cairn.api.domain.Principal;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Resolves the {@link Principal} for a request from an HTTP Basic {@code Authorization}
 * header, treating the password field as a personal access token (security doc,
 * section 2.3): the common pattern for Git-over-HTTP, where the username is
 * conventionally ignored (any value, or a placeholder like {@code x-access-token})
 * and the token itself carries the identity.
 */
@Component
public class PrincipalResolver {

    private final PersonalAccessTokenJpaRepository tokens;
    private final TokenHasher tokenHasher;

    public PrincipalResolver(PersonalAccessTokenJpaRepository tokens, TokenHasher tokenHasher) {
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
    }

    public Principal resolve(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Principal.ANONYMOUS;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Principal.ANONYMOUS;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return Principal.ANONYMOUS;
        }
        String rawToken = decoded.substring(colon + 1);
        if (rawToken.isEmpty()) {
            return Principal.ANONYMOUS;
        }
        String hash = tokenHasher.hash(rawToken);
        return tokens.findByTokenHash(hash)
                .filter(token -> token.isValid(Instant.now()))
                .<Principal>map(token -> new Principal.UserPrincipal(token.user()))
                .orElse(Principal.ANONYMOUS);
    }
}
