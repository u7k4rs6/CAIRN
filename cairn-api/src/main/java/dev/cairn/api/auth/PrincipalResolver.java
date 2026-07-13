package dev.cairn.api.auth;

import dev.cairn.api.domain.Principal;
import dev.cairn.api.repo.PersonalAccessTokenJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Resolves the {@link Principal} for a request, trying, in order: a server-side web
 * session cookie (security doc, section 2.2 — what a real browser page uses after
 * {@code POST /api/login}), then an HTTP Basic {@code Authorization} header treating
 * the password field as a personal access token (section 2.3 — Git-over-HTTP and
 * API clients, where the username is conventionally ignored). Trying the session
 * first is what lets a Server Component's own fetch (which forwards the browser's
 * cookies, not a bearer token it has no way to read from localStorage) see a
 * private repo's content as its legitimate owner; see {@code SessionStore}'s
 * Javadoc and the frontend's {@code lib/api.ts} for the other half of this fix.
 */
@Component
public class PrincipalResolver {

    public static final String SESSION_COOKIE = "cairn_session";

    private final PersonalAccessTokenJpaRepository tokens;
    private final TokenHasher tokenHasher;
    private final SessionStore sessions;
    private final UserJpaRepository users;

    public PrincipalResolver(PersonalAccessTokenJpaRepository tokens, TokenHasher tokenHasher,
                              SessionStore sessions, UserJpaRepository users) {
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.sessions = sessions;
        this.users = users;
    }

    public Principal resolve(HttpServletRequest request) {
        Optional<Principal> sessionPrincipal = resolveFromSession(request);
        if (sessionPrincipal.isPresent()) {
            return sessionPrincipal.get();
        }
        return resolveFromBasicAuthToken(request);
    }

    private Optional<Principal> resolveFromSession(HttpServletRequest request) {
        String sessionId = sessionCookie(request);
        return sessions.resolve(sessionId)
                .flatMap(auth -> users.findById(auth.userId()))
                .map(user -> (Principal) new Principal.UserPrincipal(user));
    }

    public String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Principal resolveFromBasicAuthToken(HttpServletRequest request) {
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
