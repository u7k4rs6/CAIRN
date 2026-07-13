package dev.cairn.api.auth;

import dev.cairn.api.domain.User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side web sessions (security doc, section 2.2): the cookie carries only a
 * high-entropy session id, never any user data. In-memory, like {@link
 * dev.cairn.api.activity.InMemoryActivityFeed}: a restart logs everyone out, which
 * is the same overall-scope tradeoff as this project's H2-in-memory persistence,
 * not a shortcut specific to sessions. A real deployment would back this with a
 * shared store (Redis, a database table) so sessions survive a restart and work
 * across multiple app instances.
 */
@Component
public class SessionStore {

    private static final Duration TTL = Duration.ofHours(12);

    private record SessionRecord(Long userId, String username, String csrfToken, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final TokenGenerator tokenGenerator;

    public SessionStore(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    public record NewSession(String sessionId, String csrfToken) {
    }

    public NewSession create(User user) {
        String sessionId = tokenGenerator.generate();
        String csrfToken = tokenGenerator.generate();
        sessions.put(sessionId, new SessionRecord(user.id(), user.username(), csrfToken, Instant.now().plus(TTL)));
        return new NewSession(sessionId, csrfToken);
    }

    public record Authenticated(Long userId, String username) {
    }

    public Optional<Authenticated> resolve(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        SessionRecord record = sessions.get(sessionId);
        if (record == null || record.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(new Authenticated(record.userId(), record.username()));
    }

    /** Whether {@code candidateCsrfToken} matches the token minted for this session (double-submit cookie pattern). */
    public boolean matchesCsrfToken(String sessionId, String candidateCsrfToken) {
        SessionRecord record = sessionId == null ? null : sessions.get(sessionId);
        return record != null && candidateCsrfToken != null && record.csrfToken().equals(candidateCsrfToken);
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
