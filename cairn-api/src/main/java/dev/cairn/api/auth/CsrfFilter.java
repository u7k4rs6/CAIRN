package dev.cairn.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * The other half of {@link SessionStore}'s cookie-based auth (security doc, section
 * 2.2's CSRF requirement on state-changing requests): a browser's cookies are sent
 * automatically on every request to this origin, including ones a malicious third
 * -party page triggers, so a mutating request authenticated purely by a cookie must
 * also prove the caller's own JavaScript could read a second, non-{@code HttpOnly}
 * cookie ({@code cairn_csrf}) and echo it back as a header (the double-submit
 * pattern) — a cross-site attacker's forged request carries the session cookie
 * automatically but cannot read or replay that second cookie's value.
 *
 * <p>Requests authenticated by a personal access token over Basic auth (Git-over-
 * HTTP, API clients) are not subject to this check: nothing about that credential
 * is attached to a request automatically by a browser, so there is no ambient
 * -credential forgery risk for it to defend against. The check only fires when a
 * session cookie is present on the request at all, regardless of whether the
 * request also happens to carry other credentials.
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final PrincipalResolver principalResolver;
    private final SessionStore sessions;

    public CsrfFilter(PrincipalResolver principalResolver, SessionStore sessions) {
        this.principalResolver = principalResolver;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String sessionId = principalResolver.sessionCookie(request);
        if (sessionId == null) {
            chain.doFilter(request, response);
            return;
        }
        String csrfHeader = request.getHeader("X-CSRF-Token");
        if (!sessions.matchesCsrfToken(sessionId, csrfHeader)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"missing or invalid CSRF token\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
