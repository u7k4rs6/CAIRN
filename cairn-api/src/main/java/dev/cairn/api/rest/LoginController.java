package dev.cairn.api.rest;

import dev.cairn.api.auth.PasswordHasher;
import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.auth.SessionStore;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Real server-side web sessions (security doc, section 2.2), the P2 gap-closure
 * replacement for the localStorage-PAT stand-in the first build shipped: a
 * {@code cairn_session} cookie ({@code HttpOnly}, {@code SameSite=Lax}, and
 * {@code Secure} when {@code cairn.cookie-secure} is enabled, off by default only
 * so local HTTP development still works) plus a readable {@code cairn_csrf} cookie
 * the frontend echoes back as a header on every mutation (the double-submit
 * pattern: no server-side CSRF-token storage needed beyond the session itself,
 * see {@link SessionStore}). {@link dev.cairn.api.CsrfFilter} is the other half,
 * enforcing that echo for session-cookie-authenticated writes.
 */
@RestController
public class LoginController {

    private final UserJpaRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessions;
    private final PrincipalResolver principalResolver;
    private final boolean cookieSecure;

    public LoginController(UserJpaRepository users, PasswordHasher passwordHasher, SessionStore sessions,
                            PrincipalResolver principalResolver,
                            @Value("${cairn.cookie-secure:false}") boolean cookieSecure) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        this.principalResolver = principalResolver;
        this.cookieSecure = cookieSecure;
    }

    public record LoginRequest(String username, String password) {
    }

    public record MeView(String username, String email) {
    }

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletResponse response) {
        User user = users.findByUsername(body.username()).orElse(null);
        if (user == null || !passwordHasher.matches(body.password(), user.passwordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid username or password"));
        }
        SessionStore.NewSession session = sessions.create(user);
        addCookie(response, PrincipalResolver.SESSION_COOKIE, session.sessionId(), true);
        addCookie(response, "cairn_csrf", session.csrfToken(), false);
        return ResponseEntity.ok(new MeView(user.username(), user.email()));
    }

    @PostMapping("/api/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.invalidate(principalResolver.sessionCookie(request));
        addCookie(response, PrincipalResolver.SESSION_COOKIE, "", true);
        addCookie(response, "cairn_csrf", "", false);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new MeView(up.user().username(), up.user().email()));
    }

    private void addCookie(HttpServletResponse response, String name, String value, boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(value.isEmpty() ? 0 : 12 * 60 * 60);
        response.addCookie(cookie);
    }
}
